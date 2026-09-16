package ratelimiter.algo;

import ratelimiter.RateLimiter;
import ratelimiter.clock.Clock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window counter - the practical middle ground (this is what most API
 * gateways actually ship).
 *
 * Keep only two counters per client: current window and previous window. Estimate
 * the rolling count by weighting the previous window by how much of it still
 * overlaps the last `window` milliseconds.
 *
 *   estimate = prevCount * (1 - elapsedInCurrentWindow / window) + currentCount
 *
 * O(1) memory, and it smooths out the fixed-window boundary burst. It is an
 * approximation - it assumes the previous window's traffic was evenly spread.
 */
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private static class Counter {
        long windowStart;
        int currentCount;
        int previousCount;
    }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(int limit, long windowMillis, Clock clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public boolean allow(String clientId) {
        Counter counter = counters.computeIfAbsent(clientId, k -> new Counter());
        long now = clock.nowMillis();
        long currentWindowStart = now - (now % windowMillis);

        synchronized (counter) {
            if (counter.windowStart != currentWindowStart) {
                // one window forward -> old current becomes previous, more than one -> everything is stale
                boolean consecutive = currentWindowStart - counter.windowStart == windowMillis;
                counter.previousCount = consecutive ? counter.currentCount : 0;
                counter.currentCount = 0;
                counter.windowStart = currentWindowStart;
            }

            double elapsedRatio = (double) (now - currentWindowStart) / windowMillis;
            double estimate = counter.previousCount * (1.0 - elapsedRatio) + counter.currentCount;

            if (estimate < limit) {
                counter.currentCount++;
                return true;
            }
            return false;
        }
    }

    @Override
    public String name() {
        return "SlidingWindowCounter(limit=" + limit + "/" + windowMillis + "ms)";
    }
}
