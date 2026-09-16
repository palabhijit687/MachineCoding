package bookmyshow.model;

import java.util.List;

public class Booking {

    private final String id;
    private final String showId;
    private final String userId;
    private final List<String> seatIds;
    private final double amount;
    private final long createdAtMillis;
    private final long holdExpiresAtMillis;

    private BookingStatus status = BookingStatus.PENDING_PAYMENT;

    public Booking(String id, String showId, String userId, List<String> seatIds,
                   double amount, long createdAtMillis, long holdExpiresAtMillis) {
        this.id = id;
        this.showId = showId;
        this.userId = userId;
        this.seatIds = List.copyOf(seatIds);
        this.amount = amount;
        this.createdAtMillis = createdAtMillis;
        this.holdExpiresAtMillis = holdExpiresAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getShowId() {
        return showId;
    }

    public String getUserId() {
        return userId;
    }

    public List<String> getSeatIds() {
        return seatIds;
    }

    public double getAmount() {
        return amount;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getHoldExpiresAtMillis() {
        return holdExpiresAtMillis;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public boolean isHoldExpired(long nowMillis) {
        return status == BookingStatus.PENDING_PAYMENT && nowMillis >= holdExpiresAtMillis;
    }

    @Override
    public String toString() {
        return id + " show=" + showId + " user=" + userId + " seats=" + seatIds
                + " amount=" + String.format("%.2f", amount) + " " + status;
    }
}
