package cab.model;

public class Vehicle {

    private final String registrationNumber;
    private final String model;
    private final VehicleType type;

    public Vehicle(String registrationNumber, String model, VehicleType type) {
        this.registrationNumber = registrationNumber;
        this.model = model;
        this.type = type;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getModel() {
        return model;
    }

    public VehicleType getType() {
        return type;
    }

    @Override
    public String toString() {
        return model + " " + registrationNumber + " [" + type + "]";
    }
}
