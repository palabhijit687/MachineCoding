package ratelimiter;

import ratelimiter.RateLimiterFactory.Type;
import ratelimiter.algo.FixedWindowRateLimiter;
import ratelimiter.algo.TokenBucketRateLimiter;
import ratelimiter.clock.FakeClock;
import ratelimiter.clock.SystemClock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demo. Uses a FakeClock so the output is deterministic instead of relying on
 * Thread.sleep, plus one real multi-threaded check at the end.
 */
public class Main {

    private static final int LIMIT = 5;
    private static final long WINDOW = 1000; // 1 second

    public static void main(String[] args) throws Exception {
        basicBehaviour();
        windowResetBehaviour();
        boundaryBurstComparison();
        tokenBucketRefill();
        perClientIsolation();
        threadSafetyCheck();
    }

    /** 7 requests back to back with a limit of 5 -> first 5 pass. */
    private static void basicBehaviour() {
        System.out.println("=== 1. limit 5 per second, 7 requests at the same instant ===");
        for (Type type : Type.values()) {
            FakeClock clock = new FakeClock(0);
            RateLimiter limiter = RateLimiterFactory.create(type, LIMIT, WINDOW, clock);
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i <= 7; i++) {
                sb.append(limiter.allow("user-1") ? "A " : "X ");
            }
            System.out.printf("  %-48s %s%n", limiter.name(), sb);
        }
        System.out.println("  (A = allowed, X = throttled)\n");
    }

    /** After the window passes the client gets its quota back. */
    private static void windowResetBehaviour() {
        System.out.println("=== 2. exhaust the quota, advance 1s, try again ===");
        for (Type type : Type.values()) {
            FakeClock clock = new FakeClock(0);
            RateLimiter limiter = RateLimiterFactory.create(type, LIMIT, WINDOW, clock);
            for (int i = 0; i < LIMIT; i++) {
                limiter.allow("user-1");
            }
            boolean blockedBefore = !limiter.allow("user-1");
            clock.advance(WINDOW);
            boolean allowedAfter = limiter.allow("user-1");
            System.out.printf("  %-48s blocked before=%s, allowed after 1s=%s%n",
                    limiter.name(), blockedBefore, allowedAfter);
        }
        System.out.println();
    }

    /**
     * The classic fixed-window flaw: 5 requests just before the boundary and 5
     * just after = 10 requests inside 40ms. The others hold the line.
     */
    private static void boundaryBurstComparison() {
        System.out.println("=== 3. boundary burst: 5 requests at t=980ms, 5 more at t=1020ms ===");
        for (Type type : Type.values()) {
            FakeClock clock = new FakeClock(980);
            RateLimiter limiter = RateLimiterFactory.create(type, LIMIT, WINDOW, clock);

            int allowed = 0;
            for (int i = 0; i < LIMIT; i++) {
                if (limiter.allow("user-1")) allowed++;
            }
            clock.advance(40); // cross the window boundary
            for (int i = 0; i < LIMIT; i++) {
                if (limiter.allow("user-1")) allowed++;
            }
            System.out.printf("  %-48s allowed %d/10 within 40ms%n", limiter.name(), allowed);
        }
        System.out.println("  -> fixed window lets 10 through, that is why it is the weakest option\n");
    }

    /** Token bucket refills gradually and never overflows past capacity. */
    private static void tokenBucketRefill() {
        System.out.println("=== 4. token bucket partial refill ===");
        FakeClock clock = new FakeClock(0);
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(LIMIT, LIMIT, clock);

        int drained = drain(limiter);
        System.out.println("  drained the full bucket after " + drained + " requests");

        clock.advance(400); // 0.4s * 5 tokens/s = 2 tokens
        System.out.println("  after 400ms -> " + drain(limiter) + " more allowed (expected 2)");

        clock.advance(5000); // long idle must cap at capacity, not overflow
        System.out.println("  after 5s idle -> " + drain(limiter) + " allowed (capped at capacity " + LIMIT + ")\n");
    }

    /** One noisy client must not eat another client's quota. */
    private static void perClientIsolation() {
        System.out.println("=== 5. per client isolation ===");
        FakeClock clock = new FakeClock(0);
        RateLimiter limiter = new TokenBucketRateLimiter(LIMIT, LIMIT, clock);

        int userA = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allow("user-A")) userA++;
        }
        int userB = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allow("user-B")) userB++;
        }
        System.out.println("  user-A allowed " + userA + ", user-B allowed " + userB
                + " -> B is unaffected by A's burst\n");
    }

    /** 200 threads, limit 50, real clock, long window. Exactly 50 must pass. */
    private static void threadSafetyCheck() throws Exception {
        System.out.println("=== 6. thread safety: 200 concurrent requests, limit 50 ===");
        RateLimiter limiter = new FixedWindowRateLimiter(50, 60_000, new SystemClock());
        AtomicInteger allowed = new AtomicInteger();
        int threads = 200;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(32);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    startGate.await(); // release everyone together to maximise contention
                    if (limiter.allow("hot-user")) {
                        allowed.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.println("  allowed = " + allowed.get() + " (expected exactly 50) -> "
                + (allowed.get() == 50 ? "PASS" : "FAIL, counter is racy"));
    }

    private static int drain(RateLimiter limiter) {
        int count = 0;
        while (limiter.allow("user-1")) {
            count++;
        }
        return count;
    }
}
