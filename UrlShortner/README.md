# URL Shortener

Build a TinyURL / bit.ly style service: long url in, short url out, redirect back.

## How to run

```bash
javac -d out $(find src -name "*.java")
java -cp out urlshortener.Main
```

## Clarifying questions I would ask first

1. **Scale?** Reads-to-writes ratio, urls per day? *(assumed: read heavy, so `expand()` must be O(1). Single node in-memory for this exercise)*
2. **Short code length and charset?** *(assumed: base62 `[0-9a-zA-Z]`, 7 chars)*
3. **Custom aliases?** *(assumed: yes, with validation and reserved words)*
4. **Expiry / TTL?** *(assumed: optional TTL per link, null = permanent)*
5. **Same long url twice - same code or a new one?** *(assumed: reuse the code for permanent links, but a link with a TTL or a custom alias always gets its own record)*
6. **Do we track clicks?** *(assumed: yes, a hit counter. Full analytics is out of scope)*
7. **Auth / who owns a link?** *(assumed: store a `createdBy`, no real auth)*
8. **Codes guessable or not?** Sequential is shorter, random is safer - built both behind one interface.
9. **Actual HTTP redirect?** *(assumed: out of scope, `expand()` returns the long url and a controller would send a 301/302)*

## Approach

```
UrlMapping (code, longUrl, createdBy, createdAt, expiresAt, hits)
ShortCodeGenerator (interface) -> CounterCodeGenerator | RandomCodeGenerator
Base62Encoder (encode / decode / validate)
UrlRepository (interface) -> InMemoryUrlRepository (two ConcurrentHashMaps)
UrlShortenerService -> shorten / expand / stats / delete
Clock (interface) -> FakeClock in the demo
```

Key decisions and why:

- **Counter + base62 as the default generator.** Uniqueness is guaranteed by construction, so there is no collision check and no retry loop. Compare with hashing the url (MD5/SHA + truncate) which *does* collide and needs a "check then retry" loop.
- **Counter starts at 62^6** so the first code is already 7 chars. Looks like a real product and is not trivially enumerable from `1`.
- **`RandomCodeGenerator` also provided** for the "codes must not be guessable" follow-up. The service has a bounded retry loop (5 attempts) so it works with either generator - that is the whole point of the interface.
- **Two maps in the repository**: `code -> mapping` for the hot redirect path, and `longUrl -> code` for idempotency. The reverse index only holds permanent links, since a TTL link must not be handed out to a later caller with a different TTL.
- **`putIfAbsent` for claiming a code.** Atomic, so concurrent `shorten()` calls cannot overwrite each other, and no service-level lock is needed. The 500-thread demo confirms 500 distinct codes and zero lost writes.
- **Lazy expiry on read.** No background sweeper thread: `expand()` checks the timestamp, and deletes the record if it has passed. An expired alias is also reclaimable by a new link, which the demo shows.
- **Repository behind an interface.** Swapping in MySQL + Redis is a new class, not a service rewrite.
- **`expand()` accepts both** `abc123` and `https://sho.rt/abc123` so the caller does not have to strip the domain.
- **URL validation with `java.net.URI`** rather than a hand-rolled regex - rejects malformed input, non-http(s) schemes, and missing hosts.

## Edge cases handled (see the demo output)

- Empty / blank / malformed url, `ftp://` scheme, missing host -> rejected
- Shortening a url that already belongs to this service -> rejected (no redirect loops)
- Same long url twice -> same code, one stored record
- Custom alias already taken -> `AliasAlreadyTakenException`
- Reserved alias (`admin`, `api`, `stats` ...) -> rejected
- Alias too short / too long / non-alphanumeric -> rejected
- Expired link -> `ShortUrlExpiredException`, record cleaned up, alias becomes reusable
- Unknown code on expand -> `ShortUrlNotFoundException`; on delete -> returns `false` instead of throwing
- `stats()` does not inflate the click count
- Base62 round trip verified for 0, 1, 61, 62 and boundary values
- 500 concurrent shortens -> 500 distinct codes

## Extensions I would mention if time was left

- **Storage:** urls in MySQL/DynamoDB keyed by code, plus Redis in front, since redirects are far more frequent than creates.
- **Distributed ID generation:** a single `AtomicLong` does not survive multiple app servers. Options: a ticket server / ZooKeeper, Snowflake ids, or give each node a pre-allocated counter range (the simplest and what I would pick).
- **Analytics:** push click events to Kafka and aggregate offline instead of an in-row counter, which becomes a write hotspot on popular links.
- **Expiry sweeper:** a TTL index in the DB or Redis `EXPIRE` so dead rows do not accumulate; lazy deletion alone never reclaims links nobody visits.
- **Abuse handling:** rate limit creation per user, and screen destination urls against a malware denylist.
- **301 vs 302:** 301 caches in the browser and kills click tracking, so a real product uses 302.
