package urlshortener.repository;

import urlshortener.model.UrlMapping;

import java.util.Optional;

/**
 * Behind an interface so the in-memory map can be swapped for a DB/cache without
 * touching the service.
 */
public interface UrlRepository {

    /**
     * Saves only if the code is free.
     * @return true if saved, false if the code was already taken
     */
    boolean saveIfAbsent(UrlMapping mapping);

    Optional<UrlMapping> findByCode(String shortCode);

    /** Reverse lookup so shortening the same url twice returns the same code. */
    Optional<UrlMapping> findPermanentByLongUrl(String longUrl);

    boolean delete(String shortCode);

    int count();
}
