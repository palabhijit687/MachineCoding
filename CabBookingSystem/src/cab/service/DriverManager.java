package cab.service;

import cab.exception.EntityNotFoundException;
import cab.model.Driver;
import cab.model.DriverStatus;
import cab.model.Location;
import cab.model.VehicleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns driver state: location, availability, and the one operation that must be
 * atomic - reserving a driver for a trip.
 *
 * `tryReserve` is the heart of this design. Two riders can both see the same driver in
 * a nearby search, so the AVAILABLE -> ON_TRIP transition has to be a
 * compare-and-set. Whoever loses simply tries the next driver.
 */
public class DriverManager {

    private final Map<String, Driver> drivers = new ConcurrentHashMap<>();

    public Driver register(Driver driver) {
        if (drivers.containsKey(driver.getId())) {
            throw new IllegalArgumentException("driver " + driver.getId() + " already registered");
        }
        drivers.put(driver.getId(), driver);
        return driver;
    }

    public Driver get(String driverId) {
        Driver driver = drivers.get(driverId);
        if (driver == null) {
            throw new EntityNotFoundException("no driver with id " + driverId);
        }
        return driver;
    }

    public void updateLocation(String driverId, Location location) {
        get(driverId).setCurrentLocation(location);
    }

    public void goOffline(String driverId) {
        Driver driver = get(driverId);
        synchronized (this) {
            if (driver.getStatus() == DriverStatus.ON_TRIP) {
                throw new IllegalStateException(driver.getName()
                        + " is on a trip and cannot go offline");
            }
            driver.setStatus(DriverStatus.OFFLINE);
        }
    }

    public void goOnline(String driverId) {
        Driver driver = get(driverId);
        synchronized (this) {
            if (driver.getStatus() == DriverStatus.OFFLINE) {
                driver.setStatus(DriverStatus.AVAILABLE);
            }
        }
    }

    /**
     * Available drivers of the requested type inside the radius, nearest first.
     * A linear scan is fine at interview scale; a real system would use a geo index
     * (quadtree / geohash / Redis GEO) so this is not O(all drivers).
     */
    public List<Driver> findNearby(Location pickup, double radiusKm, VehicleType type) {
        List<Driver> nearby = new ArrayList<>();
        for (Driver driver : drivers.values()) {
            if (driver.getStatus() != DriverStatus.AVAILABLE) {
                continue;
            }
            if (type != null && driver.getVehicle().getType() != type) {
                continue;
            }
            if (driver.getCurrentLocation().distanceKmTo(pickup) <= radiusKm) {
                nearby.add(driver);
            }
        }
        nearby.sort(Comparator
                .comparingDouble((Driver d) -> d.getCurrentLocation().distanceKmTo(pickup))
                .thenComparing(Driver::getId));
        return nearby;
    }

    /**
     * Atomically claim a driver. Returns false when somebody else got there first.
     */
    public synchronized boolean tryReserve(String driverId) {
        Driver driver = get(driverId);
        if (driver.getStatus() != DriverStatus.AVAILABLE) {
            return false;
        }
        driver.setStatus(DriverStatus.ON_TRIP);
        return true;
    }

    /** Trip finished or cancelled - driver is free again, wherever they ended up. */
    public synchronized void release(String driverId, Location newLocation) {
        Driver driver = get(driverId);
        if (newLocation != null) {
            driver.setCurrentLocation(newLocation);
        }
        driver.setStatus(DriverStatus.AVAILABLE);
    }

    public List<Driver> allDrivers() {
        List<Driver> all = new ArrayList<>(drivers.values());
        all.sort(Comparator.comparing(Driver::getId));
        return all;
    }

    public long countByStatus(DriverStatus status) {
        return drivers.values().stream().filter(d -> d.getStatus() == status).count();
    }
}
