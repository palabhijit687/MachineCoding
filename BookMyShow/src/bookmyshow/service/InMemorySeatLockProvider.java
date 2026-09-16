package bookmyshow.service;

import bookmyshow.clock.Clock;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory locks with a TTL.
 *
 * Locking is synchronized on the per-show map, so the check-then-claim across a whole
 * seat list is atomic. That is what stops two users grabbing the same seat.
 *
 * Expired locks are treated as absent and cleaned up lazily on read - no sweeper thread.
 */
public class InMemorySeatLockProvider implements SeatLockProvider {

    private final Map<String, Map<String, SeatLock>> locksByShow = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemorySeatLockProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean lockSeats(String showId, List<String> seatIds, String userId,
                             String bookingId, long ttlSeconds) {
        Map<String, SeatLock> showLocks = locksForShow(showId);
        long now = clock.nowMillis();

        synchronized (showLocks) {
            // first pass: is anything already held by somebody else?
            for (String seatId : seatIds) {
                SeatLock existing = showLocks.get(seatId);
                if (existing != null && !existing.isExpired(now)) {
                    return false;
                }
            }
            // second pass: claim them all
            long expiresAt = now + ttlSeconds * 1000;
            for (String seatId : seatIds) {
                showLocks.put(seatId, new SeatLock(seatId, showId, userId, bookingId, expiresAt));
            }
            return true;
        }
    }

    @Override
    public void unlockSeats(String showId, List<String> seatIds) {
        Map<String, SeatLock> showLocks = locksForShow(showId);
        synchronized (showLocks) {
            seatIds.forEach(showLocks::remove);
        }
    }

    @Override
    public boolean holdsAllLocks(String showId, List<String> seatIds, String bookingId) {
        Map<String, SeatLock> showLocks = locksForShow(showId);
        long now = clock.nowMillis();
        synchronized (showLocks) {
            for (String seatId : seatIds) {
                SeatLock lock = showLocks.get(seatId);
                if (lock == null || lock.isExpired(now) || !lock.getBookingId().equals(bookingId)) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public Set<String> lockedSeats(String showId) {
        Map<String, SeatLock> showLocks = locksForShow(showId);
        long now = clock.nowMillis();
        Set<String> held = new LinkedHashSet<>();
        synchronized (showLocks) {
            showLocks.entrySet().removeIf(e -> e.getValue().isExpired(now));
            held.addAll(showLocks.keySet());
        }
        return held;
    }

    private Map<String, SeatLock> locksForShow(String showId) {
        return locksByShow.computeIfAbsent(showId, k -> new HashMap<>());
    }
}
