package cab.model;

public enum TripStatus {
    /** Driver assigned, on the way to pick up. */
    ASSIGNED,
    /** OTP verified, ride in progress. */
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
