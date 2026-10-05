package com.meditrack.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * A minimal in-memory sliding-window rate limiter, keyed by client IP.
 * Good enough for a single-instance deployment; for multi-instance
 * deployments swap this for a Redis-backed limiter (e.g. Bucket4j + Redis)
 * without changing any calling code, since callers only see
 * {@link #isAllowed(String)}.
 */
@Component
public class LoginRateLimiter {

    @Value("${app.rate-limit.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.login.window-seconds:60}")
    private int windowSeconds;

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    public boolean isAllowed(String key) {
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);

        Deque<Instant> attempts = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst().isBefore(windowStart)) {
                attempts.pollFirst();
            }
            if (attempts.size() >= maxAttempts) {
                return false;
            }
            attempts.addLast(now);
            return true;
        }
    }

    /** Call on successful login so a legitimate user isn't penalized by their own past failed attempts. */
    public void reset(String key) {
        attemptsByKey.remove(key);
    }
}
