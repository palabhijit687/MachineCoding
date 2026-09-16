package bookmyshow.service;

import java.util.List;
import java.util.Set;

/**
 * Temporary seat holds. Separate from booking on purpose: this is the piece that
 * would move to Redis (SETNX + TTL) or a DB row lock in a real deployment, and the
 * booking flow should not have to change when it does.
 */
public interface SeatLockProvider {

    /**
     * All or nothing: either every seat gets locked for this booking, or none do.
     * @return true when all seats were locked
     */
    boolean lockSeats(String showId, List<String> seatIds, String userId,
                      String bookingId, long ttlSeconds);

    void unlockSeats(String showId, List<String> seatIds);

    /** True when every seat is still locked by this booking (nothing expired). */
    boolean holdsAllLocks(String showId, List<String> seatIds, String bookingId);

    /** Seats currently held by an unexpired lock. */
    Set<String> lockedSeats(String showId);
}
