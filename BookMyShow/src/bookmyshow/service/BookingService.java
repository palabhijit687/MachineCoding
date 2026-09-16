package bookmyshow.service;

import bookmyshow.clock.Clock;
import bookmyshow.exception.InvalidBookingException;
import bookmyshow.exception.SeatNotAvailableException;
import bookmyshow.model.Booking;
import bookmyshow.model.BookingStatus;
import bookmyshow.model.Seat;
import bookmyshow.model.SeatStatus;
import bookmyshow.model.Show;
import bookmyshow.payment.PaymentGateway;
import bookmyshow.pricing.PricingStrategy;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The booking flow: hold seats -> pay -> confirm.
 *
 * Two-phase on purpose. Marking seats BOOKED straight away means an abandoned payment
 * blocks the seat forever; a TTL hold releases it automatically.
 *
 * Concurrency: one ReentrantLock per show. Everything that reads-then-writes seat state
 * for a show runs inside that lock, so "is this seat free" and "claim it" cannot be
 * interleaved by another thread. Locking per show and not globally means a busy show
 * never blocks bookings for a different show.
 */
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 10;
    private static final long DEFAULT_HOLD_SECONDS = 300; // 5 minutes

    private final Catalog catalog;
    private final SeatLockProvider lockProvider;
    private final PricingStrategy pricingStrategy;
    private final PaymentGateway paymentGateway;
    private final Clock clock;
    private final long holdSeconds;

    private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
    /** showId -> seatId -> bookingId that owns it */
    private final Map<String, Map<String, String>> bookedSeats = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> showLocks = new ConcurrentHashMap<>();
    private final AtomicInteger bookingSequence = new AtomicInteger();

    public BookingService(Catalog catalog, SeatLockProvider lockProvider,
                          PricingStrategy pricingStrategy, PaymentGateway paymentGateway,
                          Clock clock) {
        this(catalog, lockProvider, pricingStrategy, paymentGateway, clock, DEFAULT_HOLD_SECONDS);
    }

    public BookingService(Catalog catalog, SeatLockProvider lockProvider,
                          PricingStrategy pricingStrategy, PaymentGateway paymentGateway,
                          Clock clock, long holdSeconds) {
        this.catalog = catalog;
        this.lockProvider = lockProvider;
        this.pricingStrategy = pricingStrategy;
        this.paymentGateway = paymentGateway;
        this.clock = clock;
        this.holdSeconds = holdSeconds;
    }

    // ---------- availability ----------

    public Map<String, SeatStatus> seatMap(String showId) {
        Show show = requireShow(showId);
        expireStaleHolds();

        Map<String, String> booked = bookedSeats.getOrDefault(showId, Map.of());
        Set<String> locked = lockProvider.lockedSeats(showId);

        Map<String, SeatStatus> map = new LinkedHashMap<>();
        for (Seat seat : show.getScreen().getSeats()) {
            SeatStatus status = booked.containsKey(seat.getId()) ? SeatStatus.BOOKED
                    : locked.contains(seat.getId()) ? SeatStatus.HELD
                    : SeatStatus.AVAILABLE;
            map.put(seat.getId(), status);
        }
        return map;
    }

    public List<String> availableSeats(String showId) {
        return seatMap(showId).entrySet().stream()
                .filter(e -> e.getValue() == SeatStatus.AVAILABLE)
                .map(Map.Entry::getKey)
                .toList();
    }

    public double quote(String showId, List<String> seatIds) {
        Show show = requireShow(showId);
        double total = 0;
        for (String seatId : seatIds) {
            Seat seat = requireSeat(show, seatId);
            total += pricingStrategy.priceFor(show, seat);
        }
        return round2(total);
    }

    // ---------- booking flow ----------

    /** Phase 1: hold the seats and price them. */
    public Booking holdSeats(String showId, String userId, List<String> seatIds) {
        Show show = requireShow(showId);
        validateSeatRequest(show, userId, seatIds);
        expireStaleHolds();

        ReentrantLock lock = showLocks.computeIfAbsent(showId, k -> new ReentrantLock());
        lock.lock();
        try {
            List<String> unavailable = unavailableSeats(showId, seatIds);
            if (!unavailable.isEmpty()) {
                throw new SeatNotAvailableException("seats already taken: " + unavailable);
            }

            String bookingId = "BKG-" + bookingSequence.incrementAndGet();
            if (!lockProvider.lockSeats(showId, seatIds, userId, bookingId, holdSeconds)) {
                // someone else won the race between our check and the claim
                throw new SeatNotAvailableException("seats just got held by another user: " + seatIds);
            }

            long now = clock.nowMillis();
            Booking booking = new Booking(bookingId, showId, userId, seatIds,
                    quote(showId, seatIds), now, now + holdSeconds * 1000);
            bookings.put(bookingId, booking);
            return booking;
        } finally {
            lock.unlock();
        }
    }

    /** Phase 2: charge the card and turn the hold into a real booking. */
    public Booking confirmBooking(String bookingId) {
        Booking booking = requireBooking(bookingId);
        ReentrantLock lock = showLocks.computeIfAbsent(booking.getShowId(), k -> new ReentrantLock());
        lock.lock();
        try {
            if (booking.getStatus() != BookingStatus.PENDING_PAYMENT) {
                throw new InvalidBookingException("booking " + bookingId + " is "
                        + booking.getStatus() + ", cannot confirm");
            }
            if (booking.isHoldExpired(clock.nowMillis())) {
                releaseHold(booking, BookingStatus.EXPIRED);
                throw new InvalidBookingException("hold on " + bookingId
                        + " expired, the seats were released");
            }
            if (!lockProvider.holdsAllLocks(booking.getShowId(), booking.getSeatIds(), bookingId)) {
                releaseHold(booking, BookingStatus.EXPIRED);
                throw new InvalidBookingException("lost the seat hold for " + bookingId);
            }

            if (!paymentGateway.charge(bookingId, booking.getAmount())) {
                releaseHold(booking, BookingStatus.CANCELLED);
                throw new InvalidBookingException("payment failed for " + bookingId
                        + ", seats released");
            }

            Map<String, String> booked = bookedSeats.computeIfAbsent(
                    booking.getShowId(), k -> new ConcurrentHashMap<>());
            booking.getSeatIds().forEach(seatId -> booked.put(seatId, bookingId));
            lockProvider.unlockSeats(booking.getShowId(), booking.getSeatIds());
            booking.setStatus(BookingStatus.CONFIRMED);
            return booking;
        } finally {
            lock.unlock();
        }
    }

    public Booking cancelBooking(String bookingId) {
        Booking booking = requireBooking(bookingId);
        ReentrantLock lock = showLocks.computeIfAbsent(booking.getShowId(), k -> new ReentrantLock());
        lock.lock();
        try {
            switch (booking.getStatus()) {
                case CONFIRMED -> {
                    Map<String, String> booked = bookedSeats.getOrDefault(
                            booking.getShowId(), new ConcurrentHashMap<>());
                    booking.getSeatIds().forEach(booked::remove);
                    booking.setStatus(BookingStatus.CANCELLED);
                }
                case PENDING_PAYMENT -> releaseHold(booking, BookingStatus.CANCELLED);
                default -> throw new InvalidBookingException("booking " + bookingId
                        + " is already " + booking.getStatus());
            }
            return booking;
        } finally {
            lock.unlock();
        }
    }

    /** Frees any hold whose TTL has passed. Called before every availability read. */
    public int expireStaleHolds() {
        long now = clock.nowMillis();
        int expired = 0;
        for (Booking booking : bookings.values()) {
            if (booking.isHoldExpired(now)) {
                releaseHold(booking, BookingStatus.EXPIRED);
                expired++;
            }
        }
        return expired;
    }

    public Booking getBooking(String bookingId) {
        return requireBooking(bookingId);
    }

    public List<Booking> bookingsOf(String userId) {
        return bookings.values().stream()
                .filter(b -> b.getUserId().equals(userId))
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .toList();
    }

    // ---------- helpers ----------

    private void releaseHold(Booking booking, BookingStatus newStatus) {
        lockProvider.unlockSeats(booking.getShowId(), booking.getSeatIds());
        booking.setStatus(newStatus);
    }

    private List<String> unavailableSeats(String showId, List<String> seatIds) {
        Map<String, String> booked = bookedSeats.getOrDefault(showId, Map.of());
        Set<String> locked = lockProvider.lockedSeats(showId);
        List<String> unavailable = new ArrayList<>();
        for (String seatId : seatIds) {
            if (booked.containsKey(seatId) || locked.contains(seatId)) {
                unavailable.add(seatId);
            }
        }
        return unavailable;
    }

    private void validateSeatRequest(Show show, String userId, List<String> seatIds) {
        if (userId == null || userId.isBlank()) {
            throw new InvalidBookingException("user id is required");
        }
        if (seatIds == null || seatIds.isEmpty()) {
            throw new InvalidBookingException("pick at least one seat");
        }
        if (seatIds.size() > MAX_SEATS_PER_BOOKING) {
            throw new InvalidBookingException("cannot book more than "
                    + MAX_SEATS_PER_BOOKING + " seats in one booking");
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {
            throw new InvalidBookingException("the same seat is listed twice: " + seatIds);
        }
        for (String seatId : seatIds) {
            requireSeat(show, seatId);
        }
        if (!now().isBefore(show.getStartTime())) {
            throw new InvalidBookingException("show " + show.getId() + " has already started");
        }
    }

    private LocalDateTime now() {
        return Instant.ofEpochMilli(clock.nowMillis()).atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    private Show requireShow(String showId) {
        Show show = catalog.getShow(showId);
        if (show == null) {
            throw new InvalidBookingException("no show with id " + showId);
        }
        return show;
    }

    private Seat requireSeat(Show show, String seatId) {
        Seat seat = show.getScreen().getSeat(seatId);
        if (seat == null) {
            throw new InvalidBookingException("seat " + seatId + " does not exist in screen "
                    + show.getScreen().getName());
        }
        return seat;
    }

    private Booking requireBooking(String bookingId) {
        Booking booking = bookings.get(bookingId);
        if (booking == null) {
            throw new InvalidBookingException("no booking with id " + bookingId);
        }
        return booking;
    }

    private double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** Seat ids grouped by status, for printing. */
    public Map<SeatStatus, Set<String>> seatsByStatus(String showId) {
        Map<SeatStatus, Set<String>> grouped = new LinkedHashMap<>();
        for (SeatStatus status : SeatStatus.values()) {
            grouped.put(status, new LinkedHashSet<>());
        }
        seatMap(showId).forEach((seatId, status) -> grouped.get(status).add(seatId));
        return grouped;
    }
}
