# Rate Limiter

Design an API rate limiter. Same interface, four pluggable algorithms.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out ratelimiter.Main
```

## Clarifying questions I would ask first

1. **What do we limit on?** user id, API key, IP, or endpoint+user combo? *(assumed: a generic `clientId` string, so any of them works)*
2. **What is the rule?** "N requests per X seconds" or "N per minute with burst allowance"? *(assumed: N per window, configurable)*
3. **Single process or distributed?** In-memory map or Redis? *(assumed: single JVM, in-memory. Called out the Redis version as the extension)*
4. **On throttle** do we reject (429) or queue/wait? *(assumed: reject, return a boolean)*
5. **Are all clients equal** or do we need tiers (free vs paid)? *(assumed: same limit for everyone, config per client is a small change)*
6. **Which algorithm?** Usually the interviewer says "your choice, explain the tradeoff" - so I built the interface plus four implementations.
7. **Thread safety** - will multiple request threads hit this? *(assumed: yes, this is the main thing being tested)*
8. **Fail open or fail closed** if the limiter itself breaks? *(assumed: fail open, availability over strictness)*

## Approach

One interface, four implementations, a clock abstraction, and per-client state in a `ConcurrentHashMap`.

```
RateLimiter (interface)  -> allow(clientId): boolean
   FixedWindowRateLimiter
   SlidingWindowLogRateLimiter
   SlidingWindowCounterRateLimiter
   TokenBucketRateLimiter
Clock (interface) -> SystemClock, FakeClock
RateLimiterFactory -> create(type, limit, window, clock)
```

Key decisions and why:

- **`Clock` is injected.** The whole demo runs on a `FakeClock` I advance by hand, so output is deterministic and tests take 0ms instead of sleeping for seconds. This is the detail interviewers look for in this question.
- **`ConcurrentHashMap<String, State>` + `synchronized` on the per-client state object.** Lock granularity is one client, so unrelated clients never contend. A single `synchronized` method on the limiter would serialise the whole service.
- **`computeIfAbsent`** to create state, so two threads for a new client cannot create two buckets.
- **Refill computed lazily** in the token bucket from elapsed time. No background thread, no scheduler to manage.
- **Windows aligned to boundaries** (`now - now % window`) in the counter algorithms so every client shares the same window edges - easier to reason about and matches what a shared Redis key would do.

## Algorithm tradeoffs (the actual point of this question)

| Algorithm | Memory / client | Boundary burst | Notes |
|---|---|---|---|
| Fixed window | O(1) - one counter | **Yes, up to 2x the limit** | Cheapest, least accurate |
| Sliding window log | O(limit) timestamps | No, exact | Precise but memory heavy at high limits |
| Sliding window counter | O(1) - two counters | Mostly smoothed | Approximates, assumes even spread. What most gateways ship |
| Token bucket | O(1) - tokens + timestamp | No | Allows a controlled burst up to capacity, smooth long-run rate. **My default pick** |

Demo scenario 3 shows this concretely: 5 requests at t=980ms and 5 more at t=1020ms. Fixed window allows all 10 inside 40ms, sliding log and token bucket allow only 5, sliding counter allows 6.

**One result that looks wrong but is not:** in scenario 2 the sliding window counter still blocks right at t=1000ms. At that instant the previous window's 5 requests carry full weight (`5 * 1.0 + 0 = 5`), so the estimate is still at the limit. That is the algorithm working as designed - the quota bleeds back gradually instead of resetting in one jump.

## Edge cases handled (see the demo output)

- Quota exhausted, then restored after the window passes
- Window-boundary burst (fixed window vs the rest)
- Token bucket partial refill - 400ms at 5 tokens/s gives back exactly 2
- Long idle period does **not** overflow the bucket past capacity
- Per-client isolation - one noisy client does not consume another's quota
- 200 threads racing on one client with a limit of 50 -> exactly 50 pass
- Invalid config (limit or window <= 0) rejected in the constructor
- First-ever request for an unknown client creates state without a race

## Extensions I would mention if time was left

- **Distributed:** move state to Redis. Fixed/sliding counter = `INCR` + `EXPIRE`; sliding log = a sorted set with `ZREMRANGEBYSCORE`; token bucket = a small Lua script so refill-and-take is atomic. Or Redis `CL.THROTTLE`.
- **Per-tier limits:** a `LimitConfigProvider` keyed by client so free/paid users get different quotas.
- **Cost-weighted requests:** `allow(clientId, cost)` - a heavy endpoint takes 5 tokens instead of 1.
- **Return metadata** instead of a boolean: remaining quota and `Retry-After`, so the API can send proper `X-RateLimit-*` headers.
- **Eviction:** idle client state must be evicted (TTL map / Caffeine) or the map leaks memory.
- **Leaky bucket** if the requirement is queueing instead of rejecting.
