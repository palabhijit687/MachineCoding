package ratelimiter.algo;

import ratelimiter.RateLimiter;
import ratelimiter.clock.Clock;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window log.
 *
 * Keep the timestamp of every allowed request in a deque. On each call drop the
 * timestamps older than `now - window` and compare the remaining size to limit.
 *
 * Exact - no boundary burst. Cost is memory: O(limit) timestamps per client, so
 * this hurts when the limit is 10k/min and there are millions of clients.
 */
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(int limit, long windowMillis, Clock clock) {
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public boolean allow(String clientId) {
        Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        long now = clock.nowMillis();
        long cutoff = now - windowMillis;

        synchronized (log) {
            while (!log.isEmpty() && log.peekFirst() <= cutoff) {
                log.pollFirst();
            }
            if (log.size() < limit) {
                log.addLast(now);
                return true;
            }
            return false;
        }
    }

    @Override
    public String name() {
        return "SlidingWindowLog(limit=" + limit + "/" + windowMillis + "ms)";
    }
}
