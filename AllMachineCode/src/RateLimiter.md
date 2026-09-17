# Rate Limiter System Design (Fixed Window Counter)

## 1. Clarifying Questions

1. Granularity & Identifiers:
    - Are we rate limiting by client IP, user ID, API key, or globally across all requests?

2. Window Duration & Limits:
    - What is the fixed time window (e.g., per second, per minute, per hour)?
    - What is the maximum allowed request count per window?

3. Response on Limit Exceeded:
    - Should blocked requests be dropped immediately, queued, or rejected with a cooldown duration?

4. Concurrency:
    - Will requests arrive concurrently across multiple threads requiring synchronized access?

---

## 2. Requirements

### Functional Requirements
- The system must track request counts for each unique client within a fixed time window.
- The system must allow requests if the current counter is within the threshold.
- The system must block requests once the threshold is exceeded during the active window.
- The system must reset the request counter automatically once the time window expires.

### Non-Functional Requirements
- Simplicity: Clear, minimal object-oriented structure without heavy external libraries.
- Low Latency: O(1) time complexity for evaluating each incoming request.
- Memory Efficiency: Client records store only a timestamp and an integer count.

---

## 3. Enums

public enum RateLimitStatus {
ALLOWED,
BLOCKED
}

---

## 4. Models

public class ClientCounter {
private long windowStartTimeMillis;
private int requestCount;

    public ClientCounter(long windowStartTimeMillis) {
        this.windowStartTimeMillis = windowStartTimeMillis;
        this.requestCount = 1;
    }

    public long getWindowStartTimeMillis() {
        return windowStartTimeMillis;
    }

    public void setWindowStartTimeMillis(long windowStartTimeMillis) {
        this.windowStartTimeMillis = windowStartTimeMillis;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public void incrementCount() {
        this.requestCount++;
    }

    public void resetCount(long newStartTimeMillis) {
        this.windowStartTimeMillis = newStartTimeMillis;
        this.requestCount = 1;
    }
}

public class RateLimitResponse {
private RateLimitStatus status;
private int currentRequests;
private int maxRequests;
private long retryAfterSeconds;

    public RateLimitResponse(RateLimitStatus status, int currentRequests, int maxRequests, long retryAfterSeconds) {
        this.status = status;
        this.currentRequests = currentRequests;
        this.maxRequests = maxRequests;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitStatus getStatus() {
        return status;
    }

    public int getCurrentRequests() {
        return currentRequests;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

---

## 5. Controller

import java.util.HashMap;
import java.util.Map;

public class RateLimiterController {
private final int maxRequests;
private final long windowSizeMillis;
private final Map<String, ClientCounter> clientRecords;

    public RateLimiterController(int maxRequests, int windowSizeSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeSeconds * 1000L;
        this.clientRecords = new HashMap<>();
    }

    public synchronized RateLimitResponse checkRequest(String clientId) {
        long currentTime = System.currentTimeMillis();

        if (!clientRecords.containsKey(clientId)) {
            clientRecords.put(clientId, new ClientCounter(currentTime));
            return new RateLimitResponse(RateLimitStatus.ALLOWED, 1, maxRequests, 0);
        }

        ClientCounter counter = clientRecords.get(clientId);
        long timeElapsed = currentTime - counter.getWindowStartTimeMillis();

        if (timeElapsed > windowSizeMillis) {
            counter.resetCount(currentTime);
            return new RateLimitResponse(RateLimitStatus.ALLOWED, 1, maxRequests, 0);
        }

        if (counter.getRequestCount() < maxRequests) {
            counter.incrementCount();
            return new RateLimitResponse(RateLimitStatus.ALLOWED, counter.getRequestCount(), maxRequests, 0);
        }

        long remainingTimeMillis = windowSizeMillis - timeElapsed;
        long retryAfterSeconds = Math.max(1, remainingTimeMillis / 1000L);

        return new RateLimitResponse(RateLimitStatus.BLOCKED, counter.getRequestCount(), maxRequests, retryAfterSeconds);
    }
}

---

## 6. Main Class

public class Main {
public static void main(String[] args) throws InterruptedException {
// Limit: 3 requests per 2-second window
RateLimiterController limiter = new RateLimiterController(3, 2);
String clientA = "client-192.168.1.1";

        System.out.println("Sending 4 consecutive requests for Client A:");
        for (int i = 1; i <= 4; i++) {
            RateLimitResponse res = limiter.checkRequest(clientA);
            System.out.println("Request " + i + ": Status = " + res.getStatus() 
                + " | Requests: " + res.getCurrentRequests() + "/" + res.getMaxRequests()
                + " | Retry After: " + res.getRetryAfterSeconds() + "s");
        }

        System.out.println("\nWaiting 2.5 seconds for window reset...\n");
        Thread.sleep(2500);

        System.out.println("Sending request after window expiration:");
        RateLimitResponse resAfterReset = limiter.checkRequest(clientA);
        System.out.println("Request 5: Status = " + resAfterReset.getStatus() 
            + " | Requests: " + resAfterReset.getCurrentRequests() + "/" + resAfterReset.getMaxRequests());
    }
}