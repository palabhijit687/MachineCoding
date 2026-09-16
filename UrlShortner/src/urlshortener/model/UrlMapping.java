package urlshortener.model;

import java.util.concurrent.atomic.AtomicLong;

/**
 * One short code -> long url record. Also carries created/expiry time and a hit
 * counter for the stats API.
 */
public class UrlMapping {

    private final String shortCode;
    private final String longUrl;
    private final String createdBy;
    private final long createdAtMillis;
    private final Long expiresAtMillis;   // null = never expires
    private final AtomicLong hits = new AtomicLong();

    public UrlMapping(String shortCode, String longUrl, String createdBy,
                      long createdAtMillis, Long expiresAtMillis) {
        this.shortCode = shortCode;
        this.longUrl = longUrl;
        this.createdBy = createdBy;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getShortCode() {
        return shortCode;
    }

    public String getLongUrl() {
        return longUrl;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public long getHits() {
        return hits.get();
    }

    public void recordHit() {
        hits.incrementAndGet();
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis != null && nowMillis >= expiresAtMillis;
    }

    /** True only for mappings that can be reused for the same long url. */
    public boolean isPermanent() {
        return expiresAtMillis == null;
    }

    @Override
    public String toString() {
        return shortCode + " -> " + longUrl
                + " (hits=" + hits.get() + (expiresAtMillis == null ? ", no expiry" : ", expires") + ")";
    }
}
