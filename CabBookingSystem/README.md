# Cab Booking System

Uber / Ola style ride hailing. Two things carry the interview: **matching a driver**
(pluggable, and race-free) and the **trip state machine**.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out cab.Main
```

## Clarifying questions I would ask first

1. **Scope** - rider books, driver drives, trip completes? Or also scheduled rides, pooling, multi-stop? *(assumed: single on-demand ride, one rider, one driver)*
2. **How is the driver chosen?** Nearest, highest rated, or driver-accepts-request? *(assumed: system assigns automatically, ranking behind a strategy interface)*
3. **Search radius and vehicle types?** *(assumed: 5 km radius, BIKE/SEDAN/SUV, exact type match)*
4. **Fare model** - distance only, or distance + time + surge? *(assumed: base + per km + per minute, with a surge multiplier)*
5. **Is the fare fixed at booking or computed at the end?** *(assumed: estimate at booking, final fare on actual duration - the demo shows them differing)*
6. **Ride start verification** - OTP? *(assumed: yes, 4 digit OTP the rider shares with the driver)*
7. **Cancellation** - until when? Fees? *(assumed: allowed until the ride starts, no fee logic)*
8. **Ratings** *(assumed: rider rates the driver once per completed trip)*
9. **Scale** - how many drivers, how do we search geographically? *(assumed: in-memory linear scan for the exercise; called out a geo index as the real answer)*

## Approach

```
model/     Location (haversine), Vehicle, VehicleType, Driver, DriverStatus, Rider, Trip, TripStatus
strategy/  DriverMatchingStrategy -> NearestDriver | HighestRatedDriver
pricing/   FareStrategy -> DistanceBasedFareStrategy (with surge)
clock/     Clock -> FakeClock
service/   DriverManager (driver state + atomic reservation), RideService (ride lifecycle)
```

Key decisions and why:

- **`DriverManager.tryReserve()` is the core of the design.** Two riders can both see the
  same driver in a nearby search, so the `AVAILABLE -> ON_TRIP` transition is a
  synchronized compare-and-set. Nothing else may flip that flag.
- **Losing the race is not an error.** `requestRide` walks the ranked candidate list and
  takes the first driver it can actually reserve; if a reservation fails, that driver is
  dropped and the next best one is tried. The demo runs 20 riders against 3 sedans and gets
  exactly 3 rides with **3 distinct drivers** - no double booking, and nobody fails just
  because they lost one race.
- **Matching behind a strategy interface.** The candidate list is pre-filtered (available,
  right vehicle type, inside the radius) and the strategy only ranks it. The demo shows the
  same request picking a different driver under `NearestDriver` vs `HighestRatedDriver`.
- **Fare behind a strategy too**, with a surge multiplier. Estimate at request time uses an
  assumed average speed; the final fare uses the real duration from the injected clock, so
  the two legitimately differ - which is what a real receipt looks like.
- **`Clock` injected**, so a 25-minute ride happens instantly and time-based billing is
  still demonstrable.
- **Trip state machine is enforced in one place**: `ASSIGNED -> IN_PROGRESS -> COMPLETED`,
  with `CANCELLED` only reachable from `ASSIGNED`. Every transition validates the current
  state, so "end before start" or "cancel a moving car" cannot happen.
- **Driver location updates on trip events**: set to the pickup point when the ride starts,
  and to the destination when it ends, so the next match uses a truthful position.
- **New drivers default to a 4.5 rating** instead of 0, otherwise a rating-based match would
  never pick anyone new.
- **`DriverManager` and `RideService` split** - one owns driver state, the other owns the
  ride lifecycle. Reservation logic stays in exactly one class.

## Edge cases handled (see the demo output)

- **20 concurrent riders, 3 drivers -> 3 rides, 17 rejected, 3 distinct drivers assigned**
- Offline drivers are never matched; a driver on a trip is never matched
- Drivers outside the 5 km radius are excluded (Whitefield driver ignored for a
  Koramangala pickup)
- Wrong vehicle type excluded - a BIKE is not offered for a SEDAN request
- No driver anywhere near the pickup -> `NoDriverAvailableException`
- Only sedan already busy -> second rider rejected cleanly
- Wrong OTP -> ride cannot start
- Starting twice, ending before starting, ending an already completed trip -> all rejected
- Cancel before start frees the driver; cancel twice rejected; cancelling an in-progress
  ride rejected
- Rating a trip that is not completed, and rating the same trip twice -> rejected
- Rating outside 1..5, latitude/longitude out of range, surge below 1.0 -> rejected
- Duplicate driver id, unknown rider / driver / trip -> rejected
- Driver trying to go offline mid-trip -> rejected
- Source equal to destination -> rejected
- Estimated fare vs final fare differ correctly when the ride takes longer than assumed

## Extensions I would mention if time was left

- **Geo index instead of a linear scan:** quadtree, geohash buckets, S2 cells, or Redis
  `GEOSEARCH`. This is the single biggest scalability change, since `findNearby` is the
  hottest query in the system.
- **Driver accepts/rejects** the request with a timeout, falling through to the next driver -
  a small state machine on top of the current auto-assign.
- **Distributed reservation:** the in-JVM `synchronized` becomes a Redis lock or a
  conditional DB update (`UPDATE drivers SET status='ON_TRIP' WHERE id=? AND status='AVAILABLE'`),
  and the row count tells you whether you won.
- **ETA and routing** from a maps service behind an interface, replacing haversine.
- **Ride pooling** (share a route), scheduled rides, and multi-stop trips.
- **Payments and cancellation fees**, plus driver payouts and commission.
- **Surge as a live computation** from the demand/supply ratio per zone, rather than a
  constructor argument.
- **Location updates at scale:** drivers ping every few seconds, so writes dominate - that
  path wants an in-memory store with async persistence, not a relational write per ping.
