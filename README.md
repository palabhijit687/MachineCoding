# Machine Coding Practice

Low level design questions solved the way I would actually write them in a 90 minute
machine coding round: plain Java, no frameworks, no build tool, one `Main` per problem
that demonstrates the happy path plus the edge cases an interviewer probes for.

| Folder | Problem | What it exercises |
|---|---|---|
| [ParkingLot](ParkingLot) | Multi-floor parking lot with ticketing and billing | Entity modelling, strategy pattern, O(1) spot allocation |
| [RateLimiter](RateLimiter) | API rate limiter, 4 algorithms behind one interface | Algorithm tradeoffs, thread safety, clock injection |
| [UrlShortner](UrlShortner) | TinyURL style shortener | Base62 encoding, id generation, idempotency, TTL |
| [EmployeeManagementSystem](EmployeeManagementSystem) | Employee CRUD + org hierarchy + reports | Layering, criteria-object search, tree traversal, streams |
| [TicTacToe](TicTacToe) | NxN tic tac toe, 2+ players, undo | Separating board / rules / turn order, O(1) win check |
| [SnakeAndLadder](SnakeAndLadder) | Board game simulation with house rules | Config-driven rules, board validation, deterministic dice |
| [Splitwise](Splitwise) | Expense sharing with settle-up | Exact money maths, split strategies, minimum cash flow |
| [BookMyShow](BookMyShow) | Movie ticket booking | Seat hold with TTL, per-show locking, no double booking |
| [CabBookingSystem](CabBookingSystem) | Ride hailing | Driver matching strategies, atomic reservation, trip state machine |

Each folder has its own `README.md` with the clarifying questions I would ask, the
approach and the reasoning behind each design decision, the edge cases handled, and
the extensions to raise if time is left.

## Running any of them

Java 17+. From inside a problem folder:

```bash
javac -d out $(find src -name "*.java")
java -cp out <package>.Main
```

The entry point per problem:

| Folder | Main class |
|---|---|
| ParkingLot | `parkinglot.Main` |
| RateLimiter | `ratelimiter.Main` |
| UrlShortner | `urlshortener.Main` |
| EmployeeManagementSystem | `ems.Main` |
| TicTacToe | `tictactoe.Main` |
| SnakeAndLadder | `snakeandladder.Main` |
| Splitwise | `splitwise.Main` |
| BookMyShow | `bookmyshow.Main` |
| CabBookingSystem | `cab.Main` |

Or run all nine from the repo root:

```bash
./run-all.sh
```

## Conventions I follow in these rounds

- **Model first, service second.** Get the entities and enums on screen early, then one
  service class as the entry point. Interviewers grade the model more than the plumbing.
- **Interface at every seam I might be asked to change** - pricing, code generation,
  rate limit algorithm, win condition, driver matching, storage. When the follow-up is
  "now support X", the answer is a new class, not a rewrite.
- **Storage behind a repository interface**, in-memory implementation for the round,
  and I say out loud how it maps to a DB.
- **Inject the clock.** Anything with time (billing, TTL, windows, trip duration) takes a
  `Clock` or a timestamp parameter, so the demo is deterministic and nothing sleeps.
- **Money in paise, never floating point.** Splits and fares are computed in integer cents
  so nothing is silently lost to rounding.
- **Custom exceptions over booleans and nulls** for failures a caller must handle.
- **Validate at the service boundary**, so bad data never reaches the store.
- **Guard the one operation that must be atomic.** Seat booking, driver reservation and
  rate limit counters each have exactly one critical section, locked at the narrowest
  useful scope (per show, per driver, per client) rather than one global lock.
- **Demonstrate, don't claim.** Every `Main` prints the edge cases actually failing, and
  the concurrency-sensitive ones run a multi-threaded check with an expected count:
  200 threads vs a limit of 50, 500 concurrent shortens, 50 users on one pair of seats,
  20 riders for 3 drivers.
- **Comments explain the *why*** (the tradeoff, the assumption), not what the line does.
# MachineCoding
