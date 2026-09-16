package urlshortener.clock;

/**
 * Injected so the demo can jump forward in time and show expiry without sleeping.
 */
public interface Clock {

    long nowMillis();

    static Clock system() {
        return System::currentTimeMillis;
    }
}
