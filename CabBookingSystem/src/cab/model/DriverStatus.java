package cab.model;

public enum DriverStatus {
    /** Online and free to take a ride. */
    AVAILABLE,
    /** Assigned to a trip, or currently driving one. */
    ON_TRIP,
    OFFLINE
}
