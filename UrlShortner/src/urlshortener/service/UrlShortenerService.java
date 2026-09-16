package urlshortener.service;

import urlshortener.clock.Clock;
import urlshortener.encoder.Base62Encoder;
import urlshortener.encoder.ShortCodeGenerator;
import urlshortener.exception.AliasAlreadyTakenException;
import urlshortener.exception.ShortUrlExpiredException;
import urlshortener.exception.ShortUrlNotFoundException;
import urlshortener.model.UrlMapping;
import urlshortener.repository.UrlRepository;

import java.net.URI;
import java.util.Optional;
import java.util.Set;

/**
 * The API surface:
 *   shorten(longUrl)                         -> permanent, reused if already shortened
 *   shorten(longUrl, alias, ttl, createdBy)  -> custom alias and/or expiry
 *   expand(shortUrlOrCode)                   -> long url, counts a hit
 *   stats(code) / delete(code)
 */
public class UrlShortenerService {

    private static final int MAX_ALIAS_LENGTH = 20;
    private static final int MIN_ALIAS_LENGTH = 3;
    private static final int MAX_COLLISION_RETRIES = 5;

    /** Cannot be handed out as aliases because they clash with our own routes. */
    private static final Set<String> RESERVED = Set.of("api", "admin", "login", "signup", "stats", "health");

    private final String domain;
    private final UrlRepository repository;
    private final ShortCodeGenerator codeGenerator;
    private final Clock clock;

    public UrlShortenerService(String domain, UrlRepository repository,
                               ShortCodeGenerator codeGenerator, Clock clock) {
        this.domain = domain.endsWith("/") ? domain : domain + "/";
        this.repository = repository;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
    }

    /** Permanent short url. Calling it twice for the same long url returns the same code. */
    public String shorten(String longUrl) {
        return shorten(longUrl, null, null, "anonymous");
    }

    /**
     * @param customAlias optional, user picked code
     * @param ttlSeconds  optional, null means never expires
     */
    public String shorten(String longUrl, String customAlias, Long ttlSeconds, String createdBy) {
        String normalizedUrl = validateAndNormalize(longUrl);
        long now = clock.nowMillis();
        Long expiresAt = ttlSeconds == null ? null : now + ttlSeconds * 1000;

        if (customAlias != null) {
            return saveWithAlias(normalizedUrl, customAlias, createdBy, now, expiresAt);
        }

        // idempotency: reuse an existing permanent code for the same url
        if (expiresAt == null) {
            Optional<UrlMapping> existing = repository.findPermanentByLongUrl(normalizedUrl);
            if (existing.isPresent()) {
                return domain + existing.get().getShortCode();
            }
        }

        // retry loop matters only for a random generator; the counter one never collides
        for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
            String code = codeGenerator.nextCode();
            if (RESERVED.contains(code)) {
                continue;
            }
            UrlMapping mapping = new UrlMapping(code, normalizedUrl, createdBy, now, expiresAt);
            if (repository.saveIfAbsent(mapping)) {
                return domain + code;
            }
        }
        throw new IllegalStateException("could not generate a unique short code, please retry");
    }

    /** Returns the long url and counts the hit. */
    public String expand(String shortUrlOrCode) {
        String code = extractCode(shortUrlOrCode);
        UrlMapping mapping = repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException("no url for code " + code));

        if (mapping.isExpired(clock.nowMillis())) {
            repository.delete(code); // lazy cleanup on read
            throw new ShortUrlExpiredException("short url " + code + " has expired");
        }
        mapping.recordHit();
        return mapping.getLongUrl();
    }

    /** Stats without counting a hit. */
    public UrlMapping stats(String shortUrlOrCode) {
        String code = extractCode(shortUrlOrCode);
        return repository.findByCode(code)
                .orElseThrow(() -> new ShortUrlNotFoundException("no url for code " + code));
    }

    public boolean delete(String shortUrlOrCode) {
        return repository.delete(extractCode(shortUrlOrCode));
    }

    public int totalUrls() {
        return repository.count();
    }

    private String saveWithAlias(String longUrl, String alias, String createdBy,
                                 long now, Long expiresAt) {
        validateAlias(alias);
        UrlMapping mapping = new UrlMapping(alias, longUrl, createdBy, now, expiresAt);
        if (!repository.saveIfAbsent(mapping)) {
            // an expired mapping is squatting on the alias - free it and take the alias
            Optional<UrlMapping> current = repository.findByCode(alias);
            if (current.isPresent() && current.get().isExpired(now)) {
                repository.delete(alias);
                if (repository.saveIfAbsent(mapping)) {
                    return domain + alias;
                }
            }
            throw new AliasAlreadyTakenException("alias '" + alias + "' is already in use");
        }
        return domain + alias;
    }

    private void validateAlias(String alias) {
        if (alias.length() < MIN_ALIAS_LENGTH || alias.length() > MAX_ALIAS_LENGTH) {
            throw new IllegalArgumentException(
                    "alias must be " + MIN_ALIAS_LENGTH + "-" + MAX_ALIAS_LENGTH + " characters");
        }
        if (!Base62Encoder.isValidCode(alias)) {
            throw new IllegalArgumentException("alias must be alphanumeric only");
        }
        if (RESERVED.contains(alias.toLowerCase())) {
            throw new IllegalArgumentException("alias '" + alias + "' is reserved");
        }
    }

    private String validateAndNormalize(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        String trimmed = longUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("malformed url: " + longUrl);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("only http/https urls are supported: " + longUrl);
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url must have a host: " + longUrl);
        }
        if (trimmed.startsWith(domain) || trimmed.startsWith("http://" + stripScheme(domain))) {
            throw new IllegalArgumentException("cannot shorten a url from this service");
        }
        return trimmed;
    }

    /** Accepts both "abc123" and "https://sho.rt/abc123". */
    private String extractCode(String shortUrlOrCode) {
        if (shortUrlOrCode == null || shortUrlOrCode.isBlank()) {
            throw new IllegalArgumentException("short url is required");
        }
        String value = shortUrlOrCode.trim();
        int lastSlash = value.lastIndexOf('/');
        return lastSlash >= 0 ? value.substring(lastSlash + 1) : value;
    }

    private String stripScheme(String url) {
        return url.replaceFirst("^https?://", "");
    }
}
