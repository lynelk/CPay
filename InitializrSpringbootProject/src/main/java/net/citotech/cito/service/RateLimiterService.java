package net.citotech.cito.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-merchant API rate limiter using the token bucket algorithm.
 * Default: 60 requests per minute per merchant.
 */
@Service
public class RateLimiterService {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private static final int DEFAULT_REQUESTS_PER_MINUTE = 60;

    private Bucket createBucket(int requestsPerMinute) {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute,
                Refill.greedy(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Try to consume a request token for the given merchant.
     * @param merchantNumber the merchant account number
     * @param requestsPerMinute the limit (0 = use default)
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean tryConsume(String merchantNumber, int requestsPerMinute) {
        int limit = requestsPerMinute <= 0 ? DEFAULT_REQUESTS_PER_MINUTE : requestsPerMinute;
        Bucket bucket = buckets.computeIfAbsent(merchantNumber, k -> createBucket(limit));
        return bucket.tryConsume(1);
    }

    /**
     * Try to consume a request token for the given merchant using the default rate.
     */
    public boolean tryConsume(String merchantNumber) {
        return tryConsume(merchantNumber, DEFAULT_REQUESTS_PER_MINUTE);
    }
}
