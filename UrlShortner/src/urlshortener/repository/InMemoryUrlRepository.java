package urlshortener.repository;

import urlshortener.model.UrlMapping;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Two maps:
 *   code -> mapping        (the redirect path, must be fast)
 *   longUrl -> code        (only for permanent mappings, powers idempotency)
 *
 * putIfAbsent gives me atomic "claim this code" semantics, which is what makes
 * concurrent shorten() calls safe without a lock.
 */
public class InMemoryUrlRepository implements UrlRepository {

    private final Map<String, UrlMapping> byCode = new ConcurrentHashMap<>();
    private final Map<String, String> codeByLongUrl = new ConcurrentHashMap<>();

    @Override
    public boolean saveIfAbsent(UrlMapping mapping) {
        UrlMapping existing = byCode.putIfAbsent(mapping.getShortCode(), mapping);
        if (existing != null) {
            return false;
        }
        if (mapping.isPermanent()) {
            codeByLongUrl.putIfAbsent(mapping.getLongUrl(), mapping.getShortCode());
        }
        return true;
    }

    @Override
    public Optional<UrlMapping> findByCode(String shortCode) {
        return Optional.ofNullable(byCode.get(shortCode));
    }

    @Override
    public Optional<UrlMapping> findPermanentByLongUrl(String longUrl) {
        String code = codeByLongUrl.get(longUrl);
        if (code == null) {
            return Optional.empty();
        }
        UrlMapping mapping = byCode.get(code);
        if (mapping == null) {
            codeByLongUrl.remove(longUrl, code); // reverse index went stale after a delete
            return Optional.empty();
        }
        return Optional.of(mapping);
    }

    @Override
    public boolean delete(String shortCode) {
        UrlMapping removed = byCode.remove(shortCode);
        if (removed == null) {
            return false;
        }
        codeByLongUrl.remove(removed.getLongUrl(), shortCode);
        return true;
    }

    @Override
    public int count() {
        return byCode.size();
    }
}
