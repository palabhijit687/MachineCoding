package parkinglot.model;

import java.util.List;

/**
 * Size of a parking spot. A spot can hold a vehicle only if the vehicle fits.
 * BIKE  -> SMALL, MEDIUM, LARGE
 * CAR   -> MEDIUM, LARGE
 * TRUCK -> LARGE
 */
public enum SpotType {
    SMALL(List.of(VehicleType.BIKE)),
    MEDIUM(List.of(VehicleType.BIKE, VehicleType.CAR)),
    LARGE(List.of(VehicleType.BIKE, VehicleType.CAR, VehicleType.TRUCK));

    private final List<VehicleType> allowed;

    SpotType(List<VehicleType> allowed) {
        this.allowed = allowed;
    }

    public boolean canFit(VehicleType type) {
        return allowed.contains(type);
    }

    /**
     * Smallest spot type that can hold the given vehicle. Used to prefer a SMALL
     * spot for a bike instead of wasting a LARGE one.
     */
    public static List<SpotType> preferenceOrder(VehicleType type) {
        return switch (type) {
            case BIKE -> List.of(SMALL, MEDIUM, LARGE);
            case CAR -> List.of(MEDIUM, LARGE);
            case TRUCK -> List.of(LARGE);
        };
    }
}
