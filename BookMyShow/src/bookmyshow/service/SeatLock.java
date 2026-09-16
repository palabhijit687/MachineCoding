package bookmyshow.service;

/** A temporary claim on one seat of one show. */
public class SeatLock {

    private final String seatId;
    private final String showId;
    private final String userId;
    private final String bookingId;
    private final long expiresAtMillis;

    public SeatLock(String seatId, String showId, String userId, String bookingId,
                    long expiresAtMillis) {
        this.seatId = seatId;
        this.showId = showId;
        this.userId = userId;
        this.bookingId = bookingId;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getSeatId() {
        return seatId;
    }

    public String getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }
}
