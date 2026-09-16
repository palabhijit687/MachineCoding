package ratelimiter.clock;

/**
 * Wrapping the clock so the demo and unit tests do not need Thread.sleep.
 * This is the single most useful trick in a rate limiter interview.
 */
public interface Clock {
    long nowMillis();
}
