package ratelimiter;

import ratelimiter.algo.FixedWindowRateLimiter;
import ratelimiter.algo.SlidingWindowCounterRateLimiter;
import ratelimiter.algo.SlidingWindowLogRateLimiter;
import ratelimiter.algo.TokenBucketRateLimiter;
import ratelimiter.clock.Clock;

/**
 * Factory so callers pick an algorithm by config/enum instead of newing up a
 * concrete class everywhere.
 */
public class RateLimiterFactory {

    public enum Type {
        FIXED_WINDOW,
        SLIDING_WINDOW_LOG,
        SLIDING_WINDOW_COUNTER,
        TOKEN_BUCKET
    }

    private RateLimiterFactory() {
    }

    public static RateLimiter create(Type type, int limit, long windowMillis, Clock clock) {
        return switch (type) {
            case FIXED_WINDOW -> new FixedWindowRateLimiter(limit, windowMillis, clock);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(limit, windowMillis, clock);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(limit, windowMillis, clock);
            // for a bucket, "limit per window" maps to capacity=limit, refill=limit/window
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(limit, limit * 1000.0 / windowMillis, clock);
        };
    }
}
