package parkinglot.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * A floor keeps its free spots in a queue per spot type so that finding a spot is
 * O(1) instead of scanning every spot on the floor.
 */
public class ParkingFloor {

    private final int floorNumber;
    private final List<ParkingSpot> allSpots = new ArrayList<>();
    private final Map<SpotType, Queue<ParkingSpot>> freeSpots = new EnumMap<>(SpotType.class);

    public ParkingFloor(int floorNumber) {
        this.floorNumber = floorNumber;
        for (SpotType type : SpotType.values()) {
            freeSpots.put(type, new LinkedList<>());
        }
    }

    public int getFloorNumber() {
        return floorNumber;
    }

    public void addSpot(ParkingSpot spot) {
        allSpots.add(spot);
        freeSpots.get(spot.getType()).add(spot);
    }

    /**
     * Returns a free spot for the vehicle, preferring the smallest spot that fits,
     * or null when the floor is full for this vehicle type.
     */
    public ParkingSpot findAndOccupy(Vehicle vehicle) {
        for (SpotType type : SpotType.preferenceOrder(vehicle.getType())) {
            Queue<ParkingSpot> queue = freeSpots.get(type);
            ParkingSpot spot = queue.poll();
            if (spot != null) {
                spot.park(vehicle);
                return spot;
            }
        }
        return null;
    }

    public void release(ParkingSpot spot) {
        spot.free();
        freeSpots.get(spot.getType()).add(spot);
    }

    public int freeCount(SpotType type) {
        return freeSpots.get(type).size();
    }

    public int totalCount(SpotType type) {
        return (int) allSpots.stream().filter(s -> s.getType() == type).count();
    }

    public List<ParkingSpot> getAllSpots() {
        return List.copyOf(allSpots);
    }
}
