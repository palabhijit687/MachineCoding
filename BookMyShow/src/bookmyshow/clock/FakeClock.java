package bookmyshow.clock;

import java.util.concurrent.atomic.AtomicLong;

/** Lets the demo prove that a seat hold expires, without waiting 5 minutes. */
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
