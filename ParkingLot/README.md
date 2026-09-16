# Parking Lot

Classic LLD question. Design a parking lot that issues a ticket on entry and charges money on exit.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out parkinglot.Main
```

## Clarifying questions I would ask first

1. **Scope** - single lot or multiple lots in one city? *(assumed: single lot, multiple floors)*
2. **Vehicle types** - how many? *(assumed: BIKE, CAR, TRUCK)*
3. **Spot types** - fixed size spots or generic? Can a bike park in a car spot? *(assumed: SMALL/MEDIUM/LARGE, a bigger spot can hold a smaller vehicle)*
4. **Spot allocation** - nearest to entry gate, or any free spot? *(assumed: first free spot, smallest that fits, floor by floor)*
5. **Pricing** - hourly, slab based, flat? Different per vehicle type? *(assumed: per-hour rate per vehicle type, every started hour charged, min 1 hour)*
6. **Payment** - do I need to model cash/card/UPI? *(assumed: out of scope, just return the amount)*
7. **Reservation / monthly pass** - needed now? *(assumed: no, mentioned as extension)*
8. **Concurrency** - multiple entry gates hitting the system together? *(assumed: yes, single JVM, so guard with synchronization)*
9. **Persistence** - in-memory or DB? *(assumed: in-memory, repository can be swapped later)*

## Approach

Entities first, then one service class on top.

```
Vehicle (number, type)
ParkingSpot (id, SpotType, floorNumber, parkedVehicle)
ParkingFloor (floorNumber, spots, free-spot queue per SpotType)
Ticket (id, vehicle, spot, entryTime, exitTime, amount)
PricingStrategy (interface) <- HourlyPricingStrategy
ParkingLot (floors, activeTickets, closedTickets) -> park() / unpark() / availability()
```

Key decisions and why:

- **Free spots kept in a queue per spot type on each floor.** `park()` becomes O(1) instead of scanning every spot. On exit the spot goes back into its queue.
- **`SpotType.preferenceOrder(vehicleType)`** decides the fit order, so a bike takes a SMALL spot first and only falls back to MEDIUM/LARGE. Stops one bike from blocking a truck spot.
- **Strategy pattern for pricing.** `ParkingLot` never does arithmetic on money. Flat rate, day pass, weekend surge = new class, no change to the lot.
- **Enum holds the fit rules** (`SpotType.canFit`) instead of a big if-else in the service. Adding an EV/handicapped type is a one-line change plus a rate.
- **Time is a parameter** (`park(vehicle, entryTime)`, `unpark(id, exitTime)`). Makes the demo and unit tests deterministic instead of sleeping in tests.
- **Two ticket maps** (active + closed). A closed ticket presented again gives a clear "already closed" error instead of "unknown ticket".
- **`synchronized` on mutating methods.** Prevents two gates allocating the same spot. Called out that per-floor locks or a DB-level `SELECT ... FOR UPDATE` is the real answer at scale.

## Edge cases handled (see the demo output)

- Vehicle exits twice with the same ticket -> `InvalidTicketException`
- Unknown / fake ticket -> `InvalidTicketException`
- Same vehicle number entering twice without exiting -> `IllegalStateException`
- No spot left for that vehicle type -> `ParkingLotFullException`
- Vehicle parked less than an hour -> still charged 1 hour
- Exit time before entry time -> rejected by the pricing strategy
- Wrong-size vehicle forced into a spot -> rejected by `ParkingSpot.park`

## Extensions I would mention if time was left

- Multiple entry/exit gates as objects, each with a display board.
- `SpotAllocationStrategy` interface (nearest-to-gate, random, best-fit) so allocation is pluggable like pricing.
- Reserved spots / monthly pass holders, EV spots with a charger.
- Move maps behind a `Repository` interface and swap in a DB; make counters per-floor atomic for the availability board.
- Lost-ticket flow: charge a flat penalty using the entry time on record.
