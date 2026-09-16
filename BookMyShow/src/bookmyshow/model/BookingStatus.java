package bookmyshow.model;

public enum BookingStatus {
    /** Seats are held, waiting for payment. */
    PENDING_PAYMENT,
    CONFIRMED,
    /** Hold ran out before payment. */
    EXPIRED,
    CANCELLED
}
