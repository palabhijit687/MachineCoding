package ratelimiter;

/**
 * One method contract - "can this client make a request right now?".
 * Every algorithm implements this so the caller can swap them freely.
 */
public interface RateLimiter {

    /**
     * @param clientId whatever we rate limit on - user id, api key, ip
     * @return true if the request is allowed (and it has been counted), false if throttled
     */
    boolean allow(String clientId);

    String name();
}
