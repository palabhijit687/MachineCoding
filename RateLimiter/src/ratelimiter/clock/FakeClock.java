package ratelimiter.clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Test clock - time only moves when I tell it to.
 */
public class FakeClock implements Clock {

    private final AtomicLong now;

    public FakeClock(long startMillis) {
        this.now = new AtomicLong(startMillis);
    }

    @Override
    public long nowMillis() {
        return now.get();
    }

    public void advance(long millis) {
        now.addAndGet(millis);
    }
}
