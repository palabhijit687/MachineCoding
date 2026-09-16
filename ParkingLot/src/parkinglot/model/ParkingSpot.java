package parkinglot.model;

/**
 * One physical spot on a floor. Holds at most one vehicle at a time.
 */
public class ParkingSpot {

    private final String id;
    private final SpotType type;
    private final int floorNumber;
    private Vehicle parkedVehicle;

    public ParkingSpot(String id, SpotType type, int floorNumber) {
        this.id = id;
        this.type = type;
        this.floorNumber = floorNumber;
    }

    public String getId() {
        return id;
    }

    public SpotType getType() {
        return type;
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public boolean isFree() {
        return parkedVehicle == null;
    }

    public Vehicle getParkedVehicle() {
        return parkedVehicle;
    }

    public void park(Vehicle vehicle) {
        if (!isFree()) {
            throw new IllegalStateException("spot " + id + " is already occupied");
        }
        if (!type.canFit(vehicle.getType())) {
            throw new IllegalArgumentException(vehicle.getType() + " does not fit in a " + type + " spot");
        }
        this.parkedVehicle = vehicle;
    }

    public void free() {
        this.parkedVehicle = null;
    }

    @Override
    public String toString() {
        return id + "[" + type + "]";
    }
}
