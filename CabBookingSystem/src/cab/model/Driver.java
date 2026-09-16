package cab.model;

public class Driver {

    private final String id;
    private final String name;
    private final Vehicle vehicle;

    private Location currentLocation;
    private DriverStatus status = DriverStatus.AVAILABLE;
    private double ratingSum;
    private int ratingCount;

    public Driver(String id, String name, Vehicle vehicle, Location currentLocation) {
        this.id = id;
        this.name = name;
        this.vehicle = vehicle;
        this.currentLocation = currentLocation;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public Location getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Location currentLocation) {
        this.currentLocation = currentLocation;
    }

    public DriverStatus getStatus() {
        return status;
    }

    public void setStatus(DriverStatus status) {
        this.status = status;
    }

    public void addRating(int rating) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        ratingSum += rating;
        ratingCount++;
    }

    /** New drivers start at 4.5 so they are not buried by a lack of history. */
    public double getRating() {
        if (ratingCount == 0) {
            return 4.5;
        }
        return Math.round(ratingSum / ratingCount * 100) / 100.0;
    }

    public int getRatingCount() {
        return ratingCount;
    }

    @Override
    public String toString() {
        return name + "(" + id + ") " + vehicle.getType() + " rating=" + getRating()
                + " at " + currentLocation + " " + status;
    }
}
