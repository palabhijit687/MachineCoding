package ratelimiter.algo;

import ratelimiter.RateLimiter;
import ratelimiter.clock.Clock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed window counter.
 *
 * Time is cut into fixed buckets (0-1s, 1-2s ...). Each client gets `limit`
 * requests per bucket. Cheapest option - one counter per client.
 *
 * Known flaw: burst at the window edge. With limit=5/sec a client can fire 5 at
 * 0.999s and 5 more at 1.001s = 10 requests in ~2ms. Sliding window fixes it.
 */
public class FixedWindowRateLimiter implements RateLimiter {

    private static class Window {
        long windowStart;
        int count;
    }

    private final int limit;
    private final long windowMillis;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(int limit, long windowMillis, Clock clock) {
        if (limit <= 0 || windowMillis <= 0) {
            throw new IllegalArgumentException("limit and window must be positive");
        }
        this.limit = limit;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    @Override
    public boolean allow(String clientId) {
        Window window = windows.computeIfAbsent(clientId, k -> new Window());
        long now = clock.nowMillis();
        // align to the bucket boundary so all clients share the same window edges
        long currentWindowStart = now - (now % windowMillis);

        synchronized (window) {
            if (window.windowStart != currentWindowStart) {
                window.windowStart = currentWindowStart;
                window.count = 0;
            }
            if (window.count < limit) {
                window.count++;
                return true;
            }
            return false;
        }
    }

    @Override
    public String name() {
        return "FixedWindow(limit=" + limit + "/" + windowMillis + "ms)";
    }
}
