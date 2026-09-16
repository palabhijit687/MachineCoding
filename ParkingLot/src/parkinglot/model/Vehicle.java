package parkinglot.model;

import java.util.Objects;

public class Vehicle {

    private final String number;
    private final VehicleType type;

    public Vehicle(String number, VehicleType type) {
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("vehicle number is required");
        }
        this.number = number.trim().toUpperCase();
        this.type = Objects.requireNonNull(type, "vehicle type is required");
    }

    public String getNumber() {
        return number;
    }

    public VehicleType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + "(" + number + ")";
    }
}
