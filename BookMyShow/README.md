# BookMyShow

Movie ticket booking. The question looks like CRUD, but the whole interview is really
about one thing: **two users must never get the same seat**.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out bookmyshow.Main
```

## Clarifying questions I would ask first

1. **Scope** - browse + book, or also admin flows for adding theatres and shows? *(assumed: browse, book, cancel; admin is just catalog setup)*
2. **Search** - by city, movie, date, language? *(assumed: city + optional title, then shows for a movie on a date)*
3. **How does seat selection work?** Does the user hold seats while paying, or is booking one atomic step? *(assumed: two-phase - a TTL hold, then payment. This is the important question)*
4. **What happens if payment fails or the user abandons the flow?** *(assumed: the hold expires after 5 minutes and the seats go back on sale)*
5. **Seat types and pricing** - flat or per seat type? Weekend surcharge? *(assumed: REGULAR/PREMIUM/RECLINER multipliers on the show's base price, plus a weekend multiplier)*
6. **Max seats per booking?** *(assumed: 10)*
7. **Cancellation** - allowed? Refunds in scope? *(assumed: cancel frees the seats; refund calculation is out of scope)*
8. **Scale** - single JVM or distributed? *(assumed: single JVM with per-show locks. Said out loud that the lock provider is the piece that becomes Redis or a DB row lock)*
9. **Can one screen run overlapping shows?** *(assumed: no, rejected at catalog level)*

## Approach

```
model/    Movie, Theatre, Screen, Seat, SeatType, Show, SeatStatus, Booking, BookingStatus
pricing/  PricingStrategy -> SeatTypePricingStrategy
payment/  PaymentGateway -> FakePaymentGateway(succeed|fail)
clock/    Clock -> FakeClock
service/  Catalog (browse), SeatLockProvider (+ in-memory impl), BookingService (booking flow)
```

Key decisions and why:

- **Two-phase booking: hold, then confirm.** `holdSeats` takes a TTL lock and returns a
  `PENDING_PAYMENT` booking; `confirmBooking` charges the payment and flips it to
  `CONFIRMED`. Marking seats BOOKED at selection time means an abandoned checkout blocks
  the seat forever - the whole reason real ticketing systems have a "5 minutes remaining"
  timer.
- **`Seat` has no status field.** A seat is not free or booked in the abstract - it is free
  *for a given show*. Status is derived per show: BOOKED if in the show's booked map, HELD
  if an unexpired lock exists, otherwise AVAILABLE. One source of truth per concern, so a
  stale status field can never disagree with reality.
- **`SeatLockProvider` is an interface.** In-memory here; in production this is Redis
  `SETNX` with a TTL or a `SELECT ... FOR UPDATE`. Keeping it behind an interface means the
  booking flow does not change when the deployment does.
- **Locking is per show, not global** (`ReentrantLock` per showId). Every read-then-write on
  a show's seats happens inside that lock, so "check free" and "claim" cannot interleave.
  A sold-out blockbuster never blocks bookings for a different show.
- **All-or-nothing seat locking.** `lockSeats` checks the entire requested list first and
  only then claims it, inside one synchronized block. A partial hold on 2 of 3 seats would
  leave junk locks behind and confuse the user.
- **Lazy hold expiry.** Availability reads call `expireStaleHolds()` first, so no sweeper
  thread and no scheduled executor to manage. Expiry is also re-checked at confirm time.
- **`Clock` injected**, so the demo proves a hold expires and a show becomes unbookable
  after it starts - without sleeping for 5 minutes.
- **Pricing behind a strategy.** Seat type multipliers + weekend surcharge today; surge
  pricing or coupons is a new class, not a change to the booking flow.
- **`Catalog` (read path) separate from `BookingService` (write path).** They share nothing,
  and in a real system they scale very differently.

## Edge cases handled (see the demo output)

- **50 concurrent users going for the same 2 seats -> exactly 1 confirmed, 49 rejected**,
  and the seat map shows those seats booked once
- Requesting a seat that is already BOOKED -> `SeatNotAvailableException` naming the seat
- Requesting a seat that is currently HELD by another user -> rejected, but other free
  seats in the same row are still bookable
- Hold expiring after its TTL -> seats become AVAILABLE, booking flips to `EXPIRED`,
  confirming it fails, and another user can then grab those seats
- Payment failure -> hold released, booking `CANCELLED`, seats immediately back on sale
- Cancelling a confirmed booking -> seats freed; cancelling twice is rejected
- Confirming the same booking twice -> rejected
- Unknown show id, unknown booking id, seat that does not exist in that screen
- Empty seat selection, the same seat listed twice, blank user id, more than 10 seats
- Two overlapping shows on one screen -> rejected at catalog level
- Duplicate show id, duplicate seat id in a screen, non-positive base price
- Booking a show that has already started -> rejected
- Weekend pricing differs from weekday pricing for the same seat type

## Extensions I would mention if time was left

- **Distributed locks:** Redis `SET seat NX PX 300000`, or a unique DB constraint on
  `(show_id, seat_id)` for confirmed bookings as the final safety net. The DB constraint is
  worth having even with locks - it is the only thing that cannot be raced.
- **Payment idempotency:** an idempotency key per booking so a retried charge does not
  double-bill.
- **Refunds and cancellation policy** (full refund up to 2 hours before, then partial).
- **Seat recommendation** for "best N together in a row" - a scan over each row's free runs.
- **Notifications** (ticket email/SMS) as an observer on booking confirmation.
- **Waitlist** when a show is sold out, fed by cancellations.
- **Dynamic pricing** based on occupancy, which is just another `PricingStrategy`.
