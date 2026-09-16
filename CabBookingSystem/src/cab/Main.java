package cab;

import cab.clock.FakeClock;
import cab.exception.EntityNotFoundException;
import cab.exception.InvalidTripException;
import cab.exception.NoDriverAvailableException;
import cab.model.Driver;
import cab.model.DriverStatus;
import cab.model.Location;
import cab.model.Rider;
import cab.model.Trip;
import cab.model.Vehicle;
import cab.model.VehicleType;
import cab.pricing.DistanceBasedFareStrategy;
import cab.service.DriverManager;
import cab.service.RideService;
import cab.strategy.HighestRatedDriverMatchingStrategy;
import cab.strategy.NearestDriverMatchingStrategy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    // a few Bengaluru landmarks
    private static final Location KORAMANGALA = new Location(12.9352, 77.6245);
    private static final Location INDIRANAGAR = new Location(12.9784, 77.6408);
    private static final Location MG_ROAD = new Location(12.9750, 77.6060);
    private static final Location WHITEFIELD = new Location(12.9698, 77.7500);
    private static final Location AIRPORT = new Location(13.1986, 77.7066);

    private static FakeClock clock;
    private static DriverManager driverManager;
    private static RideService rideService;

    public static void main(String[] args) throws Exception {
        setup();
        nearbyDrivers();
        fareEstimates();
        completeRide();
        rating();
        strategySwap();
        noDriverAvailable();
        cancellation();
        concurrentRequests();
        tripHistory();
        validation();
    }

    private static void setup() {
        clock = new FakeClock(System.currentTimeMillis());
        driverManager = new DriverManager();

        driverManager.register(new Driver("d1", "Rakesh",
                new Vehicle("KA01AB1111", "Dzire", VehicleType.SEDAN),
                new Location(12.9370, 77.6270)));
        driverManager.register(new Driver("d2", "Suresh",
                new Vehicle("KA02CD2222", "Etios", VehicleType.SEDAN),
                new Location(12.9450, 77.6300)));
        driverManager.register(new Driver("d3", "Manoj",
                new Vehicle("KA03EF3333", "Innova", VehicleType.SUV),
                new Location(12.9380, 77.6250)));
        driverManager.register(new Driver("d4", "Vikram",
                new Vehicle("KA04GH4444", "Activa", VehicleType.BIKE),
                new Location(12.9360, 77.6240)));
        driverManager.register(new Driver("d5", "Farhan",
                new Vehicle("KA05IJ5555", "Amaze", VehicleType.SEDAN),
                WHITEFIELD));
        driverManager.register(new Driver("d6", "Imran",
                new Vehicle("KA06KL6666", "Ciaz", VehicleType.SEDAN),
                new Location(12.9340, 77.6230)));
        driverManager.goOffline("d6");

        rideService = new RideService(driverManager, new NearestDriverMatchingStrategy(),
                new DistanceBasedFareStrategy(), clock);
        rideService.registerRider(new Rider("r1", "Abhijeet", "9990000001"));
        rideService.registerRider(new Rider("r2", "Bhavna", "9990000002"));
    }

    private static void nearbyDrivers() {
        System.out.println("=== 1. drivers on the platform ===");
        driverManager.allDrivers().forEach(d -> System.out.println("  " + d));
        System.out.println("  (Imran is offline, Farhan is 12+ km away in Whitefield)");

        System.out.println("  SEDANs within 5 km of Koramangala:");
        rideService.nearbyDrivers(KORAMANGALA, VehicleType.SEDAN).forEach(d ->
                System.out.printf("      %-8s %.2f km away%n",
                        d.getName(), d.getCurrentLocation().distanceKmTo(KORAMANGALA)));
        System.out.println("  BIKEs within 5 km: "
                + rideService.nearbyDrivers(KORAMANGALA, VehicleType.BIKE).stream()
                .map(Driver::getName).toList());
        System.out.println();
    }

    private static void fareEstimates() {
        System.out.println("=== 2. fare estimate, Koramangala -> Indiranagar ===");
        System.out.printf("  distance: %.2f km%n", KORAMANGALA.distanceKmTo(INDIRANAGAR));
        for (VehicleType type : VehicleType.values()) {
            System.out.println("      " + rideService.fareBreakdown(type)
                    + "  ->  estimate " + rideService.estimateFare(KORAMANGALA, INDIRANAGAR, type));
        }

        RideService surged = new RideService(driverManager, new NearestDriverMatchingStrategy(),
                new DistanceBasedFareStrategy(1.8), clock);
        System.out.println("  same ride at 1.8x surge (SEDAN): "
                + surged.estimateFare(KORAMANGALA, INDIRANAGAR, VehicleType.SEDAN));
        System.out.println();
    }

    private static void completeRide() {
        System.out.println("=== 3. full ride: request -> OTP -> start -> end ===");
        Trip trip = rideService.requestRide("r1", KORAMANGALA, INDIRANAGAR, VehicleType.SEDAN);
        Driver driver = driverManager.get(trip.getDriverId());
        System.out.println("  assigned " + driver.getName() + " (nearest sedan), OTP " + trip.getOtp());
        System.out.println("  trip: " + trip);
        System.out.println("  driver status now: " + driver.getStatus());

        rideService.startTrip(trip.getId(), trip.getOtp());
        System.out.println("  OTP verified, ride started");

        clock.advanceMinutes(25);
        Trip completed = rideService.endTrip(trip.getId());
        System.out.println("  ride ended after " + completed.durationMinutes() + " minutes");
        System.out.println("  estimated " + completed.getEstimatedFare()
                + " -> final " + completed.getFinalFare() + " (billed on actual time)");
        System.out.println("  " + driver.getName() + " is " + driver.getStatus()
                + " at the drop point " + driver.getCurrentLocation() + "\n");
    }

    private static void rating() {
        System.out.println("=== 4. rating the driver ===");
        Trip trip = rideService.allTrips().get(0);
        Driver driver = driverManager.get(trip.getDriverId());
        System.out.println("  " + driver.getName() + " rating before: " + driver.getRating()
                + " (" + driver.getRatingCount() + " ratings)");
        rideService.rateDriver(trip.getId(), 4);
        System.out.println("  after a 4 star rating: " + driver.getRating()
                + " (" + driver.getRatingCount() + " ratings)");
        System.out.println();
    }

    private static void strategySwap() {
        System.out.println("=== 5. same request, different matching strategy ===");
        // Rakesh drops his passenger and drives back to Koramangala
        driverManager.updateLocation("d1", new Location(12.9370, 77.6270));
        // the farther driver has a better rating, so the two strategies disagree
        driverManager.get("d2").addRating(5);
        driverManager.get("d2").addRating(5);

        for (Driver d : rideService.nearbyDrivers(KORAMANGALA, VehicleType.SEDAN)) {
            System.out.printf("      %-8s %.2f km, rating %.2f%n",
                    d.getName(), d.getCurrentLocation().distanceKmTo(KORAMANGALA), d.getRating());
        }

        RideService byRating = new RideService(driverManager,
                new HighestRatedDriverMatchingStrategy(), new DistanceBasedFareStrategy(), clock);
        byRating.registerRider(new Rider("r1", "Abhijeet", "9990000001"));

        Trip nearestTrip = rideService.requestRide("r1", KORAMANGALA, MG_ROAD, VehicleType.SEDAN);
        System.out.println("  " + rideService.matchingStrategyName() + " picked "
                + driverManager.get(nearestTrip.getDriverId()).getName());
        rideService.cancelTrip(nearestTrip.getId());

        Trip ratedTrip = byRating.requestRide("r1", KORAMANGALA, MG_ROAD, VehicleType.SEDAN);
        System.out.println("  " + byRating.matchingStrategyName() + " picked "
                + driverManager.get(ratedTrip.getDriverId()).getName());
        byRating.cancelTrip(ratedTrip.getId());
        System.out.println();
    }

    private static void noDriverAvailable() {
        System.out.println("=== 6. nobody available ===");
        try {
            rideService.requestRide("r1", AIRPORT, MG_ROAD, VehicleType.SEDAN);
        } catch (NoDriverAvailableException e) {
            System.out.println("  far from everyone: " + e.getMessage());
        }

        // a small fleet of exactly one sedan, then two riders want it
        DriverManager smallFleet = new DriverManager();
        smallFleet.register(new Driver("x1", "Solo",
                new Vehicle("KA09ZZ9999", "Dzire", VehicleType.SEDAN), KORAMANGALA));
        RideService small = new RideService(smallFleet, new NearestDriverMatchingStrategy(),
                new DistanceBasedFareStrategy(), clock);
        small.registerRider(new Rider("r1", "Abhijeet", "9990000001"));
        small.registerRider(new Rider("r2", "Bhavna", "9990000002"));

        small.requestRide("r1", KORAMANGALA, MG_ROAD, VehicleType.SEDAN);
        System.out.println("  Abhijeet got the only sedan");
        try {
            small.requestRide("r2", KORAMANGALA, MG_ROAD, VehicleType.SEDAN);
        } catch (NoDriverAvailableException e) {
            System.out.println("  Bhavna: " + e.getMessage());
        }
        System.out.println("  offline drivers are never matched either (Imran stayed idle)\n");
    }

    private static void cancellation() {
        System.out.println("=== 7. cancellation ===");
        Trip trip = rideService.requestRide("r2", KORAMANGALA, INDIRANAGAR, VehicleType.SUV);
        Driver driver = driverManager.get(trip.getDriverId());
        System.out.println("  " + driver.getName() + " assigned, status " + driver.getStatus());

        rideService.cancelTrip(trip.getId());
        System.out.println("  cancelled -> trip " + rideService.getTrip(trip.getId()).getStatus()
                + ", driver " + driver.getStatus() + " again");

        try {
            rideService.cancelTrip(trip.getId());
        } catch (InvalidTripException e) {
            System.out.println("  cancelling twice: " + e.getMessage());
        }

        Trip started = rideService.requestRide("r2", KORAMANGALA, INDIRANAGAR, VehicleType.SUV);
        rideService.startTrip(started.getId(), started.getOtp());
        try {
            rideService.cancelTrip(started.getId());
        } catch (InvalidTripException e) {
            System.out.println("  cancelling a moving ride: " + e.getMessage());
        }
        clock.advanceMinutes(20);
        rideService.endTrip(started.getId());
        System.out.println();
    }

    /** 20 riders, 3 sedans. Exactly 3 rides, and no driver assigned twice. */
    private static void concurrentRequests() throws Exception {
        System.out.println("=== 8. 20 riders request at once, only 3 sedans ===");
        DriverManager fleet = new DriverManager();
        fleet.register(new Driver("c1", "Driver1",
                new Vehicle("KA11AA1111", "Dzire", VehicleType.SEDAN), new Location(12.9355, 77.6250)));
        fleet.register(new Driver("c2", "Driver2",
                new Vehicle("KA11AA2222", "Etios", VehicleType.SEDAN), new Location(12.9360, 77.6255)));
        fleet.register(new Driver("c3", "Driver3",
                new Vehicle("KA11AA3333", "Ciaz", VehicleType.SEDAN), new Location(12.9365, 77.6260)));

        RideService service = new RideService(fleet, new NearestDriverMatchingStrategy(),
                new DistanceBasedFareStrategy(), clock);
        int riders = 20;
        for (int i = 0; i < riders; i++) {
            service.registerRider(new Rider("rr" + i, "Rider" + i, "99900000" + i));
        }

        AtomicInteger booked = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        ConcurrentLinkedQueue<String> assignedDrivers = new ConcurrentLinkedQueue<>();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(riders);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < riders; i++) {
            String riderId = "rr" + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    Trip trip = service.requestRide(riderId, KORAMANGALA, MG_ROAD, VehicleType.SEDAN);
                    assignedDrivers.add(trip.getDriverId());
                    booked.incrementAndGet();
                } catch (NoDriverAvailableException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        startGate.countDown();
        done.await(15, TimeUnit.SECONDS);
        pool.shutdown();

        Set<String> unique = new HashSet<>(assignedDrivers);
        System.out.println("  booked = " + booked.get() + ", rejected = " + rejected.get());
        System.out.println("  drivers assigned: " + unique.size() + " distinct of "
                + assignedDrivers.size() + " assignments");
        System.out.println("  drivers still available: " + fleet.countByStatus(DriverStatus.AVAILABLE));
        boolean pass = booked.get() == 3 && rejected.get() == riders - 3
                && unique.size() == assignedDrivers.size();
        System.out.println("  " + (pass ? "PASS - no driver double booked" : "FAIL") + "\n");
    }

    private static void tripHistory() {
        System.out.println("=== 9. history ===");
        System.out.println("  Abhijeet's trips:");
        rideService.tripsOfRider("r1").forEach(t -> System.out.println("      " + t));
        System.out.println("  Bhavna's trips:");
        rideService.tripsOfRider("r2").forEach(t -> System.out.println("      " + t));
        System.out.println("  Rakesh's trips:");
        rideService.tripsOfDriver("d1").forEach(t -> System.out.println("      " + t));
        System.out.println();
    }

    private static void validation() {
        System.out.println("=== 10. validation ===");

        expect("unknown rider", () -> rideService.requestRide("r99", KORAMANGALA, MG_ROAD,
                VehicleType.SEDAN));
        expect("same source and destination", () -> rideService.requestRide("r1", KORAMANGALA,
                KORAMANGALA, VehicleType.SEDAN));
        expect("missing vehicle type", () -> rideService.requestRide("r1", KORAMANGALA, MG_ROAD, null));
        expect("unknown trip", () -> rideService.getTrip("TRIP-999"));
        expect("unknown driver", () -> driverManager.get("d99"));
        expect("duplicate driver id", () -> driverManager.register(new Driver("d1", "Copy",
                new Vehicle("KA00XX0000", "Dzire", VehicleType.SEDAN), KORAMANGALA)));
        expect("invalid latitude", () -> new Location(120, 77));
        expect("invalid rating value", () -> driverManager.get("d1").addRating(9));
        expect("surge below 1.0", () -> new DistanceBasedFareStrategy(0.5));

        Trip trip = rideService.requestRide("r1", KORAMANGALA, INDIRANAGAR, VehicleType.SEDAN);
        expect("wrong OTP", () -> rideService.startTrip(trip.getId(), "0000"));
        expect("ending before starting", () -> rideService.endTrip(trip.getId()));
        expect("rating an unfinished trip", () -> rideService.rateDriver(trip.getId(), 5));
        expect("driver going offline mid trip",
                () -> driverManager.goOffline(trip.getDriverId()));

        rideService.startTrip(trip.getId(), trip.getOtp());
        expect("starting twice", () -> rideService.startTrip(trip.getId(), trip.getOtp()));
        clock.advanceMinutes(10);
        rideService.endTrip(trip.getId());
        rideService.rateDriver(trip.getId(), 5);
        expect("rating the same trip twice", () -> rideService.rateDriver(trip.getId(), 3));
        expect("ending an already completed trip", () -> rideService.endTrip(trip.getId()));
    }

    private static void expect(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": NOT rejected (bug)");
        } catch (InvalidTripException | NoDriverAvailableException | EntityNotFoundException
                 | IllegalArgumentException | IllegalStateException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }
}
