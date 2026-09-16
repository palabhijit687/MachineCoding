package urlshortener;

import urlshortener.clock.FakeClock;
import urlshortener.encoder.Base62Encoder;
import urlshortener.encoder.CounterCodeGenerator;
import urlshortener.encoder.RandomCodeGenerator;
import urlshortener.exception.AliasAlreadyTakenException;
import urlshortener.exception.ShortUrlExpiredException;
import urlshortener.exception.ShortUrlNotFoundException;
import urlshortener.repository.InMemoryUrlRepository;
import urlshortener.repository.UrlRepository;
import urlshortener.service.UrlShortenerService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final String DOMAIN = "https://sho.rt/";

    public static void main(String[] args) throws Exception {
        FakeClock clock = new FakeClock(1_726_000_000_000L);
        UrlRepository repository = new InMemoryUrlRepository();
        UrlShortenerService service = new UrlShortenerService(
                DOMAIN, repository, new CounterCodeGenerator(), clock);

        basicShortenAndExpand(service);
        idempotency(service);
        customAlias(service);
        expiry(service, clock);
        stats(service);
        validation(service);
        base62Sanity();
        uniquenessUnderLoad();
    }

    private static void basicShortenAndExpand(UrlShortenerService service) {
        System.out.println("=== 1. shorten and expand ===");
        String longUrl = "https://www.amazon.in/dp/B0C1234567?ref=nav_signin&pd_rd_i=xyz";
        String shortUrl = service.shorten(longUrl);
        System.out.println("  long  : " + longUrl);
        System.out.println("  short : " + shortUrl);
        System.out.println("  expand: " + service.expand(shortUrl));
        System.out.println("  expand by code only: " + service.expand(codeOf(shortUrl)));
        System.out.println();
    }

    private static void idempotency(UrlShortenerService service) {
        System.out.println("=== 2. same url shortened twice ===");
        String url = "https://github.com/spring-projects/spring-boot";
        String first = service.shorten(url);
        String second = service.shorten(url);
        System.out.println("  first  : " + first);
        System.out.println("  second : " + second);
        System.out.println("  same code reused? " + first.equals(second));
        System.out.println("  total urls stored: " + service.totalUrls() + "\n");
    }

    private static void customAlias(UrlShortenerService service) {
        System.out.println("=== 3. custom alias ===");
        String shortUrl = service.shorten("https://myblog.dev/posts/lld-interview-prep",
                "lldprep", null, "abhijeet");
        System.out.println("  created: " + shortUrl);
        System.out.println("  expand : " + service.expand(shortUrl));

        try {
            service.shorten("https://someoneelse.com", "lldprep", null, "bob");
        } catch (AliasAlreadyTakenException e) {
            System.out.println("  duplicate alias blocked: " + e.getMessage());
        }
        try {
            service.shorten("https://example.com", "admin", null, "bob");
        } catch (IllegalArgumentException e) {
            System.out.println("  reserved alias blocked: " + e.getMessage());
        }
        try {
            service.shorten("https://example.com", "ab", null, "bob");
        } catch (IllegalArgumentException e) {
            System.out.println("  too short alias blocked: " + e.getMessage());
        }
        try {
            service.shorten("https://example.com", "my_alias!", null, "bob");
        } catch (IllegalArgumentException e) {
            System.out.println("  bad charset alias blocked: " + e.getMessage());
        }
        System.out.println();
    }

    private static void expiry(UrlShortenerService service, FakeClock clock) {
        System.out.println("=== 4. expiring link (ttl 60s) ===");
        String shortUrl = service.shorten("https://zoom.us/j/9999999999", "meetnow", 60L, "abhijeet");
        System.out.println("  created: " + shortUrl);
        clock.advanceSeconds(30);
        System.out.println("  after 30s -> " + service.expand(shortUrl));
        clock.advanceSeconds(31);
        try {
            service.expand(shortUrl);
        } catch (ShortUrlExpiredException e) {
            System.out.println("  after 61s -> blocked: " + e.getMessage());
        }
        // the alias is free again now that it expired
        String reused = service.shorten("https://zoom.us/j/1111111111", "meetnow", 60L, "abhijeet");
        System.out.println("  alias reclaimed after expiry: " + reused);
        System.out.println("  expand : " + service.expand(reused) + "\n");
    }

    private static void stats(UrlShortenerService service) {
        System.out.println("=== 5. click stats ===");
        String shortUrl = service.shorten("https://leetcode.com/problemset/all/", "leet", null, "abhijeet");
        for (int i = 0; i < 4; i++) {
            service.expand(shortUrl);
        }
        System.out.println("  " + service.stats(shortUrl));
        System.out.println("  created by: " + service.stats(shortUrl).getCreatedBy());
        System.out.println("  (stats() itself does not count as a hit)\n");
    }

    private static void validation(UrlShortenerService service) {
        System.out.println("=== 6. bad input ===");
        String[] badUrls = {"", "   ", "not a url", "ftp://files.example.com/a.txt", "https://"};
        for (String bad : badUrls) {
            try {
                service.shorten(bad);
                System.out.println("  accepted (unexpected): '" + bad + "'");
            } catch (IllegalArgumentException e) {
                System.out.println("  rejected '" + bad + "' -> " + e.getMessage());
            }
        }
        try {
            service.shorten(DOMAIN + "leet");
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected shortening our own url -> " + e.getMessage());
        }
        try {
            service.expand("https://sho.rt/doesNotExist");
        } catch (ShortUrlNotFoundException e) {
            System.out.println("  unknown code -> " + e.getMessage());
        }
        System.out.println("  delete unknown code returns: " + service.delete("nope123") + "\n");
    }

    private static void base62Sanity() {
        System.out.println("=== 7. base62 round trip ===");
        long[] values = {0, 1, 61, 62, 12345, 56_800_235_584L};
        for (long v : values) {
            String encoded = Base62Encoder.encode(v);
            long decoded = Base62Encoder.decode(encoded);
            System.out.printf("  %-14d -> %-8s -> %-14d %s%n",
                    v, encoded, decoded, v == decoded ? "ok" : "MISMATCH");
        }
        System.out.println();
    }

    /** 500 concurrent shortens of different urls must produce 500 distinct codes. */
    private static void uniquenessUnderLoad() throws Exception {
        System.out.println("=== 8. 500 concurrent shortens, random generator ===");
        UrlShortenerService service = new UrlShortenerService(
                DOMAIN, new InMemoryUrlRepository(), new RandomCodeGenerator(), () -> 0L);

        Set<String> codes = ConcurrentHashMap.newKeySet();
        int total = 500;
        CountDownLatch done = new CountDownLatch(total);
        ExecutorService pool = Executors.newFixedThreadPool(16);

        for (int i = 0; i < total; i++) {
            int id = i;
            pool.submit(() -> {
                try {
                    codes.add(service.shorten("https://example.com/page/" + id));
                } finally {
                    done.countDown();
                }
            });
        }
        done.await(10, TimeUnit.SECONDS);
        pool.shutdown();

        System.out.println("  distinct short urls = " + codes.size() + " of " + total);
        System.out.println("  stored mappings     = " + service.totalUrls());
        System.out.println("  " + (codes.size() == total && service.totalUrls() == total
                ? "PASS - no collisions, no lost writes" : "FAIL"));
    }

    private static String codeOf(String shortUrl) {
        return shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
    }
}
