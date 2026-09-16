package bookmyshow;

import bookmyshow.clock.FakeClock;
import bookmyshow.exception.InvalidBookingException;
import bookmyshow.exception.SeatNotAvailableException;
import bookmyshow.model.Booking;
import bookmyshow.model.Movie;
import bookmyshow.model.Screen;
import bookmyshow.model.Seat;
import bookmyshow.model.SeatStatus;
import bookmyshow.model.SeatType;
import bookmyshow.model.Show;
import bookmyshow.model.Theatre;
import bookmyshow.payment.FakePaymentGateway;
import bookmyshow.pricing.SeatTypePricingStrategy;
import bookmyshow.service.BookingService;
import bookmyshow.service.Catalog;
import bookmyshow.service.InMemorySeatLockProvider;
import bookmyshow.service.SeatLockProvider;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final LocalDate THURSDAY = LocalDate.of(2026, 9, 17);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 19);
    private static final long HOLD_SECONDS = 300;

    private static Catalog catalog;
    private static FakeClock clock;
    private static BookingService bookingService;

    public static void main(String[] args) throws Exception {
        setup();
        browse();
        seatMapAndPricing();
        happyPath();
        seatContention();
        holdExpiry();
        paymentFailure();
        cancellation();
        concurrentBooking();
        validation();
    }

    private static void setup() {
        catalog = new Catalog();

        Movie jawan = new Movie("m1", "Jawan", "Hindi", 169);
        Movie interstellar = new Movie("m2", "Interstellar", "English", 169);
        Movie kantara = new Movie("m3", "Kantara", "Kannada", 148);
        catalog.addMovie(jawan);
        catalog.addMovie(interstellar);
        catalog.addMovie(kantara);

        Theatre pvr = new Theatre("t1", "PVR Forum", "Bengaluru");
        Screen pvrScreen1 = buildScreen("s1", "Audi 1");
        pvr.addScreen(pvrScreen1);
        catalog.addTheatre(pvr);

        Theatre inox = new Theatre("t2", "INOX Garuda", "Bengaluru");
        Screen inoxScreen1 = buildScreen("s2", "Audi 1");
        inox.addScreen(inoxScreen1);
        catalog.addTheatre(inox);

        Theatre cinepolis = new Theatre("t3", "Cinepolis Viviana", "Mumbai");
        Screen mumbaiScreen = buildScreen("s3", "Audi 1");
        cinepolis.addScreen(mumbaiScreen);
        catalog.addTheatre(cinepolis);

        catalog.addShow(new Show("sh1", jawan, pvr, pvrScreen1,
                LocalDateTime.of(THURSDAY, java.time.LocalTime.of(18, 30)), 200));
        catalog.addShow(new Show("sh2", jawan, pvr, pvrScreen1,
                LocalDateTime.of(THURSDAY, java.time.LocalTime.of(22, 0)), 200));
        catalog.addShow(new Show("sh3", jawan, inox, inoxScreen1,
                LocalDateTime.of(SATURDAY, java.time.LocalTime.of(19, 0)), 250));
        catalog.addShow(new Show("sh4", interstellar, pvr, pvrScreen1,
                LocalDateTime.of(THURSDAY, java.time.LocalTime.of(14, 0)), 180));
        catalog.addShow(new Show("sh5", kantara, cinepolis, mumbaiScreen,
                LocalDateTime.of(THURSDAY, java.time.LocalTime.of(20, 0)), 220));

        // "now" is Thursday 09:00, before every show
        long startMillis = LocalDateTime.of(THURSDAY, java.time.LocalTime.of(9, 0))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        clock = new FakeClock(startMillis);

        SeatLockProvider lockProvider = new InMemorySeatLockProvider(clock);
        bookingService = new BookingService(catalog, lockProvider,
                new SeatTypePricingStrategy(), new FakePaymentGateway(true), clock, HOLD_SECONDS);
    }

    /** A = regular, B = premium, C = recliner. */
    private static Screen buildScreen(String id, String name) {
        Screen screen = new Screen(id, name);
        for (int i = 1; i <= 5; i++) {
            screen.addSeat(new Seat("A", i, SeatType.REGULAR));
        }
        for (int i = 1; i <= 5; i++) {
            screen.addSeat(new Seat("B", i, SeatType.PREMIUM));
        }
        for (int i = 1; i <= 4; i++) {
            screen.addSeat(new Seat("C", i, SeatType.RECLINER));
        }
        return screen;
    }

    private static void browse() {
        System.out.println("=== 1. browse ===");
        System.out.println("  movies playing in Bengaluru: "
                + catalog.searchMovies("Bengaluru", null).stream().map(Movie::getTitle).toList());
        System.out.println("  search 'jaw' in Bengaluru:    "
                + catalog.searchMovies("Bengaluru", "jaw").stream().map(Movie::getTitle).toList());
        System.out.println("  search 'jaw' in Mumbai:       "
                + catalog.searchMovies("Mumbai", "jaw").stream().map(Movie::getTitle).toList());
        System.out.println("  Jawan shows in Bengaluru on " + THURSDAY + ":");
        catalog.showsFor("m1", "Bengaluru", THURSDAY).forEach(s -> System.out.println("      " + s));
        System.out.println();
    }

    private static void seatMapAndPricing() {
        System.out.println("=== 2. seat map and pricing ===");
        printSeatMap("sh1");
        System.out.println("  quote A1+A2 (regular, weekday) : " + bookingService.quote("sh1", List.of("A1", "A2")));
        System.out.println("  quote B1 (premium, weekday)    : " + bookingService.quote("sh1", List.of("B1")));
        System.out.println("  quote C1 (recliner, weekday)   : " + bookingService.quote("sh1", List.of("C1")));
        System.out.println("  same seats on the Saturday show (base 250 + weekend 1.2x):");
        System.out.println("      A1 -> " + bookingService.quote("sh3", List.of("A1"))
                + ", C1 -> " + bookingService.quote("sh3", List.of("C1")));
        System.out.println();
    }

    private static void happyPath() {
        System.out.println("=== 3. book seats (hold -> pay -> confirm) ===");
        Booking booking = bookingService.holdSeats("sh1", "user-amit", List.of("A1", "A2", "B1"));
        System.out.println("  held : " + booking);
        System.out.println("  seat map while held:");
        printSeatMap("sh1");

        Booking confirmed = bookingService.confirmBooking(booking.getId());
        System.out.println("  confirmed: " + confirmed);
        System.out.println("  seat map after confirming:");
        printSeatMap("sh1");
        System.out.println();
    }

    private static void seatContention() {
        System.out.println("=== 4. two users, same seats ===");
        try {
            bookingService.holdSeats("sh1", "user-bhavna", List.of("A2", "A3"));
        } catch (SeatNotAvailableException e) {
            System.out.println("  already booked seat rejected: " + e.getMessage());
        }

        Booking held = bookingService.holdSeats("sh1", "user-bhavna", List.of("A3", "A4"));
        System.out.println("  Bhavna holds A3, A4: " + held.getStatus());
        try {
            bookingService.holdSeats("sh1", "user-chetan", List.of("A4", "A5"));
        } catch (SeatNotAvailableException e) {
            System.out.println("  seat held by someone else rejected: " + e.getMessage());
        }
        // Chetan can still take a free seat
        Booking chetan = bookingService.holdSeats("sh1", "user-chetan", List.of("A5"));
        System.out.println("  Chetan holds A5 instead: " + chetan.getStatus());
        bookingService.cancelBooking(chetan.getId());
        System.out.println("  (Chetan's hold cancelled to keep the demo tidy)\n");
    }

    private static void holdExpiry() {
        System.out.println("=== 5. hold expiry ===");
        Booking booking = bookingService.holdSeats("sh1", "user-divya", List.of("C1", "C2"));
        System.out.println("  Divya holds C1, C2 -> " + statusOf("sh1", "C1"));
        System.out.println("  advancing the clock past the " + HOLD_SECONDS + "s hold...");
        clock.advanceSeconds(HOLD_SECONDS + 1);

        System.out.println("  C1 is now " + statusOf("sh1", "C1")
                + ", booking is " + bookingService.getBooking(booking.getId()).getStatus());
        try {
            bookingService.confirmBooking(booking.getId());
        } catch (InvalidBookingException e) {
            System.out.println("  confirming an expired hold: " + e.getMessage());
        }
        // Bhavna's A3/A4 hold from scenario 4 also expired, so those are free again
        System.out.println("  Bhavna's earlier hold also lapsed -> A3 is " + statusOf("sh1", "A3"));
        Booking fresh = bookingService.holdSeats("sh1", "user-esha", List.of("C1", "C2"));
        System.out.println("  Esha can now grab C1, C2: " + fresh.getStatus());
        bookingService.cancelBooking(fresh.getId());
        System.out.println();
    }

    private static void paymentFailure() {
        System.out.println("=== 6. payment failure releases the seats ===");
        SeatLockProvider lockProvider = new InMemorySeatLockProvider(clock);
        BookingService failing = new BookingService(catalog, lockProvider,
                new SeatTypePricingStrategy(), new FakePaymentGateway(false), clock, HOLD_SECONDS);

        Booking booking = failing.holdSeats("sh2", "user-amit", List.of("B2", "B3"));
        System.out.println("  held " + booking.getSeatIds() + " on sh2, amount " + booking.getAmount());
        try {
            failing.confirmBooking(booking.getId());
        } catch (InvalidBookingException e) {
            System.out.println("  " + e.getMessage());
        }
        System.out.println("  booking status: " + failing.getBooking(booking.getId()).getStatus());
        System.out.println("  B2 is back to " + failing.seatMap("sh2").get("B2") + "\n");
    }

    private static void cancellation() {
        System.out.println("=== 7. cancelling a confirmed booking ===");
        Booking booking = bookingService.bookingsOf("user-amit").get(0);
        System.out.println("  before: " + booking);
        System.out.println("  A1 is " + statusOf("sh1", "A1"));

        bookingService.cancelBooking(booking.getId());
        System.out.println("  after : " + bookingService.getBooking(booking.getId()));
        System.out.println("  A1 is " + statusOf("sh1", "A1") + " again");
        try {
            bookingService.cancelBooking(booking.getId());
        } catch (InvalidBookingException e) {
            System.out.println("  cancelling twice: " + e.getMessage());
        }
        System.out.println();
    }

    /** 50 users go for the same 2 seats at the same instant. Exactly one must win. */
    private static void concurrentBooking() throws Exception {
        System.out.println("=== 8. 50 concurrent users, same 2 seats ===");
        SeatLockProvider lockProvider = new InMemorySeatLockProvider(clock);
        BookingService service = new BookingService(catalog, lockProvider,
                new SeatTypePricingStrategy(), new FakePaymentGateway(true), clock, HOLD_SECONDS);

        int users = 50;
        List<String> wantedSeats = List.of("B4", "B5");
        AtomicInteger confirmed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < users; i++) {
            String userId = "user-" + i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    Booking booking = service.holdSeats("sh5", userId, wantedSeats);
                    service.confirmBooking(booking.getId());
                    confirmed.incrementAndGet();
                } catch (SeatNotAvailableException e) {
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

        System.out.println("  confirmed = " + confirmed.get() + ", rejected = " + rejected.get());
        Map<SeatStatus, Set<String>> grouped = service.seatsByStatus("sh5");
        System.out.println("  booked seats on sh5: " + grouped.get(SeatStatus.BOOKED));
        System.out.println("  " + (confirmed.get() == 1 && rejected.get() == users - 1
                ? "PASS - no double booking" : "FAIL - seats were double booked") + "\n");
    }

    private static void validation() {
        System.out.println("=== 9. validation ===");

        expect("unknown show", () -> bookingService.holdSeats("sh99", "u1", List.of("A1")));
        expect("seat not in this screen", () -> bookingService.holdSeats("sh1", "u1", List.of("Z9")));
        expect("no seats selected", () -> bookingService.holdSeats("sh1", "u1", List.of()));
        expect("same seat twice", () -> bookingService.holdSeats("sh1", "u1", List.of("A1", "A1")));
        expect("blank user", () -> bookingService.holdSeats("sh1", "  ", List.of("A1")));
        expect("more than 10 seats", () -> bookingService.holdSeats("sh1", "u1",
                List.of("A1", "A2", "A3", "A4", "A5", "B1", "B2", "B3", "B4", "B5", "C1")));
        expect("unknown booking", () -> bookingService.confirmBooking("BKG-999"));

        Booking booking = bookingService.holdSeats("sh1", "user-farah", List.of("A1"));
        bookingService.confirmBooking(booking.getId());
        expect("confirming twice", () -> bookingService.confirmBooking(booking.getId()));

        expect("overlapping show on the same screen", () -> catalog.addShow(new Show("sh6",
                catalog.getMovie("m1"), null, catalog.getShow("sh1").getScreen(),
                LocalDateTime.of(THURSDAY, java.time.LocalTime.of(19, 0)), 200)));
        expect("duplicate show id", () -> catalog.addShow(new Show("sh1",
                catalog.getMovie("m1"), null, catalog.getShow("sh1").getScreen(),
                LocalDateTime.of(SATURDAY, java.time.LocalTime.of(2, 0)), 200)));
        expect("negative base price", () -> new Show("sh7", catalog.getMovie("m1"), null,
                catalog.getShow("sh1").getScreen(),
                LocalDateTime.of(SATURDAY, java.time.LocalTime.of(2, 0)), -10));
        expect("duplicate seat in a screen",
                () -> catalog.getShow("sh1").getScreen().addSeat(new Seat("A", 1, SeatType.REGULAR)));

        System.out.println("  advancing the clock to Thursday 23:00 (after every Thursday show)...");
        clock.advanceSeconds(14 * 3600);
        expect("show already started", () -> bookingService.holdSeats("sh1", "u1", List.of("A2")));
    }

    private static void printSeatMap(String showId) {
        Map<SeatStatus, Set<String>> grouped = bookingService.seatsByStatus(showId);
        System.out.println("      available: " + grouped.get(SeatStatus.AVAILABLE));
        System.out.println("      held     : " + grouped.get(SeatStatus.HELD));
        System.out.println("      booked   : " + grouped.get(SeatStatus.BOOKED));
    }

    private static SeatStatus statusOf(String showId, String seatId) {
        return bookingService.seatMap(showId).get(seatId);
    }

    private static void expect(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": NOT rejected (bug)");
        } catch (InvalidBookingException | SeatNotAvailableException | IllegalArgumentException e) {
            System.out.println("  " + label + ": " + e.getMessage());
        }
    }
}
