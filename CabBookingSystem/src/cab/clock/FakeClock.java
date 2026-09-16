package cab.clock;

import java.util.concurrent.atomic.AtomicLong;

/** So the demo can "drive for 25 minutes" instantly and bill by time. */
public class FakeClock implements Clock {

    private final AtomicLong now;

    public FakeClock(long startMillis) {
        this.now = new AtomicLong(startMillis);
    }

    @Override
    public long nowMillis() {
        return now.get();
    }

    public void advanceMinutes(long minutes) {
        now.addAndGet(minutes * 60_000);
    }
}
