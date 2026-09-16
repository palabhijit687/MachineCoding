package cab.model;

public class Trip {

    private final String id;
    private final String riderId;
    private final String driverId;
    private final Location source;
    private final Location destination;
    private final double distanceKm;
    private final VehicleType vehicleType;
    private final double estimatedFare;
    private final String otp;
    private final long requestedAtMillis;

    private TripStatus status = TripStatus.ASSIGNED;
    private long startedAtMillis;
    private long endedAtMillis;
    private double finalFare;
    private Integer driverRating;

    public Trip(String id, String riderId, String driverId, Location source, Location destination,
                double distanceKm, VehicleType vehicleType, double estimatedFare,
                String otp, long requestedAtMillis) {
        this.id = id;
        this.riderId = riderId;
        this.driverId = driverId;
        this.source = source;
        this.destination = destination;
        this.distanceKm = distanceKm;
        this.vehicleType = vehicleType;
        this.estimatedFare = estimatedFare;
        this.otp = otp;
        this.requestedAtMillis = requestedAtMillis;
    }

    public String getId() {
        return id;
    }

    public String getRiderId() {
        return riderId;
    }

    public String getDriverId() {
        return driverId;
    }

    public Location getSource() {
        return source;
    }

    public Location getDestination() {
        return destination;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public double getEstimatedFare() {
        return estimatedFare;
    }

    public String getOtp() {
        return otp;
    }

    public long getRequestedAtMillis() {
        return requestedAtMillis;
    }

    public TripStatus getStatus() {
        return status;
    }

    public void setStatus(TripStatus status) {
        this.status = status;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public void setStartedAtMillis(long startedAtMillis) {
        this.startedAtMillis = startedAtMillis;
    }

    public long getEndedAtMillis() {
        return endedAtMillis;
    }

    public void setEndedAtMillis(long endedAtMillis) {
        this.endedAtMillis = endedAtMillis;
    }

    public double getFinalFare() {
        return finalFare;
    }

    public void setFinalFare(double finalFare) {
        this.finalFare = finalFare;
    }

    public Integer getDriverRating() {
        return driverRating;
    }

    public void setDriverRating(Integer driverRating) {
        this.driverRating = driverRating;
    }

    public long durationMinutes() {
        if (startedAtMillis == 0 || endedAtMillis == 0) {
            return 0;
        }
        return (endedAtMillis - startedAtMillis) / 60000;
    }

    @Override
    public String toString() {
        return id + " rider=" + riderId + " driver=" + driverId
                + " " + String.format("%.2f", distanceKm) + "km " + vehicleType
                + " est=" + String.format("%.2f", estimatedFare)
                + (finalFare > 0 ? " final=" + String.format("%.2f", finalFare) : "")
                + " " + status;
    }
}
