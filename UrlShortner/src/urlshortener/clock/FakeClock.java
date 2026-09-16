package urlshortener.clock;

import java.util.concurrent.atomic.AtomicLong;

public class FakeClock implements Clock {

    private final AtomicLong now;

    public FakeClock(long startMillis) {
        this.now = new AtomicLong(startMillis);
    }

    @Override
    public long nowMillis() {
        return now.get();
    }

    public void advanceSeconds(long seconds) {
        now.addAndGet(seconds * 1000);
    }
}
