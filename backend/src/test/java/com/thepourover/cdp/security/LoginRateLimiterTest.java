package com.thepourover.cdp.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LoginRateLimiterTest {

    @Test
    void blocksAfterTooManyFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String ip = "203.0.113.10";
        for (int i = 0; i < 10; i++) {
            limiter.recordFailure(ip);
        }
        assertThrows(ResponseStatusException.class, () -> limiter.checkAllowed(ip));
    }

    @Test
    void successClearsFailures() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        String ip = "203.0.113.11";
        for (int i = 0; i < 9; i++) {
            limiter.recordFailure(ip);
        }
        limiter.recordSuccess(ip);
        assertDoesNotThrow(() -> limiter.checkAllowed(ip));
    }
}
