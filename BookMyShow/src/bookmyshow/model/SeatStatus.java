package bookmyshow.model;

public enum SeatStatus {
    AVAILABLE,
    /** Someone is in the payment flow; the hold expires after a TTL. */
    HELD,
    BOOKED
}
