package ratelimiter.algo;

import ratelimiter.RateLimiter;
import ratelimiter.clock.Clock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket.
 *
 * Bucket holds up to `capacity` tokens and refills at `refillPerSecond`. A request
 * costs 1 token. Refill is computed lazily on each call - no background thread.
 *
 * This is the one I would pick by default: O(1) memory, smooth long-run rate, and
 * it still allows a short burst up to the bucket capacity, which is usually what
 * product teams want.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static class Bucket {
        double tokens;
        long lastRefillMillis;
    }

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(int capacity, double refillPerSecond, Clock clock) {
        if (capacity <= 0 || refillPerSecond <= 0) {
            throw new IllegalArgumentException("capacity and refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
    }

    @Override
    public boolean allow(String clientId) {
        long now = clock.nowMillis();
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> {
            Bucket b = new Bucket();
            b.tokens = capacity;          // start full
            b.lastRefillMillis = now;
            return b;
        });

        synchronized (bucket) {
            refill(bucket, now);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    private void refill(Bucket bucket, long now) {
        long elapsed = now - bucket.lastRefillMillis;
        if (elapsed <= 0) {
            return;
        }
        double newTokens = (elapsed / 1000.0) * refillPerSecond;
        bucket.tokens = Math.min(capacity, bucket.tokens + newTokens);
        bucket.lastRefillMillis = now;
    }

    @Override
    public String name() {
        return "TokenBucket(capacity=" + capacity + ", refill=" + refillPerSecond + "/s)";
    }
}
