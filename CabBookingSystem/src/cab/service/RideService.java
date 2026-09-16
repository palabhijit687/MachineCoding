package cab.service;

import cab.clock.Clock;
import cab.exception.EntityNotFoundException;
import cab.exception.InvalidTripException;
import cab.exception.NoDriverAvailableException;
import cab.model.Driver;
import cab.model.Location;
import cab.model.Rider;
import cab.model.Trip;
import cab.model.TripStatus;
import cab.model.VehicleType;
import cab.pricing.FareStrategy;
import cab.strategy.DriverMatchingStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The ride lifecycle: request -> assign -> start (OTP) -> end -> rate.
 *
 * Matching and pricing are both injected, so "match by rating instead" or "add surge"
 * needs no change here.
 */
public class RideService {

    private static final double DEFAULT_SEARCH_RADIUS_KM = 5.0;
    private static final double AVERAGE_SPEED_KMPH = 25.0;

    private final DriverManager driverManager;
    private final DriverMatchingStrategy matchingStrategy;
    private final FareStrategy fareStrategy;
    private final Clock clock;
    private final double searchRadiusKm;

    private final Map<String, Rider> riders = new ConcurrentHashMap<>();
    private final Map<String, Trip> trips = new ConcurrentHashMap<>();
    private final AtomicInteger tripSequence = new AtomicInteger();

    public RideService(DriverManager driverManager, DriverMatchingStrategy matchingStrategy,
                       FareStrategy fareStrategy, Clock clock) {
        this(driverManager, matchingStrategy, fareStrategy, clock, DEFAULT_SEARCH_RADIUS_KM);
    }

    public RideService(DriverManager driverManager, DriverMatchingStrategy matchingStrategy,
                       FareStrategy fareStrategy, Clock clock, double searchRadiusKm) {
        this.driverManager = driverManager;
        this.matchingStrategy = matchingStrategy;
        this.fareStrategy = fareStrategy;
        this.clock = clock;
        this.searchRadiusKm = searchRadiusKm;
    }

    public Rider registerRider(Rider rider) {
        if (riders.containsKey(rider.getId())) {
            throw new IllegalArgumentException("rider " + rider.getId() + " already registered");
        }
        riders.put(rider.getId(), rider);
        return rider;
    }

    /** What the rider sees before confirming. */
    public double estimateFare(Location source, Location destination, VehicleType type) {
        double distance = source.distanceKmTo(destination);
        return fareStrategy.calculate(type, distance, estimatedMinutes(distance));
    }

    public List<Driver> nearbyDrivers(Location pickup, VehicleType type) {
        return driverManager.findNearby(pickup, searchRadiusKm, type);
    }

    /**
     * Books a ride. Walks the ranked candidate list and takes the first driver it can
     * actually reserve, so losing a race just means the next best driver.
     */
    public Trip requestRide(String riderId, Location source, Location destination,
                            VehicleType type) {
        requireRider(riderId);
        if (source == null || destination == null) {
            throw new InvalidTripException("source and destination are required");
        }
        if (source.sameAs(destination)) {
            throw new InvalidTripException("source and destination cannot be the same place");
        }
        if (type == null) {
            throw new InvalidTripException("vehicle type is required");
        }

        List<Driver> candidates = new ArrayList<>(
                driverManager.findNearby(source, searchRadiusKm, type));
        if (candidates.isEmpty()) {
            throw new NoDriverAvailableException("no " + type + " available within "
                    + searchRadiusKm + " km of " + source);
        }

        while (!candidates.isEmpty()) {
            Optional<Driver> picked = matchingStrategy.pick(candidates, source);
            if (picked.isEmpty()) {
                break;
            }
            Driver driver = picked.get();
            if (driverManager.tryReserve(driver.getId())) {
                return createTrip(riderId, driver, source, destination, type);
            }
            // somebody else grabbed this driver first - drop them and try the next
            candidates.remove(driver);
        }
        throw new NoDriverAvailableException(
                "all nearby " + type + " drivers were taken while booking");
    }

    private Trip createTrip(String riderId, Driver driver, Location source,
                            Location destination, VehicleType type) {
        double distance = source.distanceKmTo(destination);
        int sequence = tripSequence.incrementAndGet();
        String tripId = "TRIP-" + sequence;
        String otp = String.valueOf(1000 + (sequence * 7919) % 9000);

        Trip trip = new Trip(tripId, riderId, driver.getId(), source, destination, distance,
                type, fareStrategy.calculate(type, distance, estimatedMinutes(distance)),
                otp, clock.nowMillis());
        trips.put(tripId, trip);
        return trip;
    }

    /** Driver has reached the pickup point and the rider shares the OTP. */
    public Trip startTrip(String tripId, String otp) {
        Trip trip = requireTrip(tripId);
        if (trip.getStatus() != TripStatus.ASSIGNED) {
            throw new InvalidTripException("trip " + tripId + " is " + trip.getStatus()
                    + ", cannot start");
        }
        if (!trip.getOtp().equals(otp)) {
            throw new InvalidTripException("wrong OTP for trip " + tripId);
        }
        trip.setStatus(TripStatus.IN_PROGRESS);
        trip.setStartedAtMillis(clock.nowMillis());
        driverManager.updateLocation(trip.getDriverId(), trip.getSource());
        return trip;
    }

    /** Ride done: bill on actual duration, free the driver at the drop point. */
    public Trip endTrip(String tripId) {
        Trip trip = requireTrip(tripId);
        if (trip.getStatus() != TripStatus.IN_PROGRESS) {
            throw new InvalidTripException("trip " + tripId + " is " + trip.getStatus()
                    + ", cannot end");
        }
        trip.setEndedAtMillis(clock.nowMillis());
        trip.setFinalFare(fareStrategy.calculate(trip.getVehicleType(), trip.getDistanceKm(),
                trip.durationMinutes()));
        trip.setStatus(TripStatus.COMPLETED);
        driverManager.release(trip.getDriverId(), trip.getDestination());
        return trip;
    }

    /** Only before the ride starts - once moving, it has to be ended, not cancelled. */
    public Trip cancelTrip(String tripId) {
        Trip trip = requireTrip(tripId);
        if (trip.getStatus() != TripStatus.ASSIGNED) {
            throw new InvalidTripException("trip " + tripId + " is " + trip.getStatus()
                    + ", cannot cancel");
        }
        trip.setStatus(TripStatus.CANCELLED);
        driverManager.release(trip.getDriverId(), null);
        return trip;
    }

    public Trip rateDriver(String tripId, int rating) {
        Trip trip = requireTrip(tripId);
        if (trip.getStatus() != TripStatus.COMPLETED) {
            throw new InvalidTripException("only a completed trip can be rated, this one is "
                    + trip.getStatus());
        }
        if (trip.getDriverRating() != null) {
            throw new InvalidTripException("trip " + tripId + " has already been rated");
        }
        driverManager.get(trip.getDriverId()).addRating(rating);
        trip.setDriverRating(rating);
        return trip;
    }

    public Trip getTrip(String tripId) {
        return requireTrip(tripId);
    }

    public List<Trip> tripsOfRider(String riderId) {
        requireRider(riderId);
        return trips.values().stream()
                .filter(t -> t.getRiderId().equals(riderId))
                .sorted(Comparator.comparing(Trip::getId))
                .toList();
    }

    public List<Trip> tripsOfDriver(String driverId) {
        return trips.values().stream()
                .filter(t -> t.getDriverId().equals(driverId))
                .sorted(Comparator.comparing(Trip::getId))
                .toList();
    }

    public List<Trip> allTrips() {
        return trips.values().stream().sorted(Comparator.comparing(Trip::getId)).toList();
    }

    public String fareBreakdown(VehicleType type) {
        return fareStrategy.describe(type);
    }

    public String matchingStrategyName() {
        return matchingStrategy.name();
    }

    private long estimatedMinutes(double distanceKm) {
        return Math.max(1, Math.round(distanceKm / AVERAGE_SPEED_KMPH * 60));
    }

    private Rider requireRider(String riderId) {
        Rider rider = riders.get(riderId);
        if (rider == null) {
            throw new EntityNotFoundException("no rider with id " + riderId);
        }
        return rider;
    }

    private Trip requireTrip(String tripId) {
        Trip trip = trips.get(tripId);
        if (trip == null) {
            throw new EntityNotFoundException("no trip with id " + tripId);
        }
        return trip;
    }
}
