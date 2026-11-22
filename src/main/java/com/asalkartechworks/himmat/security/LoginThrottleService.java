package com.asalkartechworks.himmat.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory bounded, expiring rate limiter for login attempts.
 * Tracks failed login attempts per normalized username and client IP address.
 */
@Slf4j
@Service
public class LoginThrottleService {

    private final int maxAttempts;
    private final int windowMinutes;
    private final Cache<String, AttemptTracker> attemptCache;

    public LoginThrottleService(
            @Value("${app.security.throttle.max-attempts:5}") int maxAttempts,
            @Value("${app.security.throttle.window-minutes:15}") int windowMinutes) {
        this.maxAttempts = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.attemptCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(windowMinutes, TimeUnit.MINUTES)
                .build();
    }

    public boolean isThrottled(String username, String clientIp) {
        String key = buildKey(username, clientIp);
        AttemptTracker tracker = attemptCache.getIfPresent(key);
        if (tracker == null) {
            return false;
        }
        boolean throttled = tracker.attempts.get() >= maxAttempts;
        if (throttled) {
            log.warn("Login attempt blocked due to throttling for key: {}", key);
        }
        return throttled;
    }

    public long getRetryAfterSeconds(String username, String clientIp) {
        String key = buildKey(username, clientIp);
        AttemptTracker tracker = attemptCache.getIfPresent(key);
        if (tracker == null) {
            return 0;
        }
        long elapsedSeconds = Duration.between(tracker.firstAttemptAt, Instant.now()).getSeconds();
        long windowSeconds = (long) windowMinutes * 60;
        long remaining = windowSeconds - elapsedSeconds;
        return Math.max(remaining, 1);
    }

    public void recordFailure(String username, String clientIp) {
        String key = buildKey(username, clientIp);
        attemptCache.asMap().compute(key, (k, existing) -> {
            if (existing == null) {
                return new AttemptTracker(new AtomicInteger(1), Instant.now());
            } else {
                existing.attempts.incrementAndGet();
                return existing;
            }
        });
    }

    public void reset(String username, String clientIp) {
        String key = buildKey(username, clientIp);
        attemptCache.invalidate(key);
    }

    private String buildKey(String username, String clientIp) {
        String normalizedUser = username != null ? username.trim().toLowerCase() : "unknown";
        String normalizedIp = clientIp != null ? clientIp.trim() : "unknown";
        return normalizedUser + ":" + normalizedIp;
    }

    private static class AttemptTracker {
        final AtomicInteger attempts;
        final Instant firstAttemptAt;

        AttemptTracker(AtomicInteger attempts, Instant firstAttemptAt) {
            this.attempts = attempts;
            this.firstAttemptAt = firstAttemptAt;
        }
    }
}
