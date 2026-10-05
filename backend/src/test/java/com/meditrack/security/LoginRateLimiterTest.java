package com.meditrack.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new LoginRateLimiter();
        ReflectionTestUtils.setField(limiter, "maxAttempts", 3);
        ReflectionTestUtils.setField(limiter, "windowSeconds", 60);
    }

    @Test
    void allowsUpToMaxAttemptsThenBlocks() {
        String key = "login:127.0.0.1";
        assertTrue(limiter.isAllowed(key));
        assertTrue(limiter.isAllowed(key));
        assertTrue(limiter.isAllowed(key));
        assertFalse(limiter.isAllowed(key), "4th attempt within the window should be blocked");
    }

    @Test
    void differentKeysAreTrackedIndependently() {
        assertTrue(limiter.isAllowed("login:1.1.1.1"));
        assertTrue(limiter.isAllowed("login:1.1.1.1"));
        assertTrue(limiter.isAllowed("login:1.1.1.1"));
        assertFalse(limiter.isAllowed("login:1.1.1.1"));

        // a different IP has its own independent bucket
        assertTrue(limiter.isAllowed("login:2.2.2.2"));
    }

    @Test
    void resetClearsAttemptsForKey() {
        String key = "login:9.9.9.9";
        limiter.isAllowed(key);
        limiter.isAllowed(key);
        limiter.isAllowed(key);
        assertFalse(limiter.isAllowed(key));

        limiter.reset(key);
        assertTrue(limiter.isAllowed(key));
    }
}
