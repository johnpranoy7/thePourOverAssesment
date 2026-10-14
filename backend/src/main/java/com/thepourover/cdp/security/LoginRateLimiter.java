package com.thepourover.cdp.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Simple per-IP login throttle (in-memory; fine for a single-node demo). */
@Component
public class LoginRateLimiter {

    private static final int MAX_FAILURES = 10;
    private static final long WINDOW_SECONDS = 15 * 60;

    private final Map<String, Window> failures = new ConcurrentHashMap<>();

    public void checkAllowed(String clientKey) {
        Window window = failures.get(normalize(clientKey));
        if (window == null) {
            return;
        }
        if (window.expired()) {
            failures.remove(normalize(clientKey), window);
            return;
        }
        if (window.count >= MAX_FAILURES) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts");
        }
    }

    public void recordFailure(String clientKey) {
        String key = normalize(clientKey);
        failures.compute(key, (k, existing) -> {
            if (existing == null || existing.expired()) {
                return new Window(1, Instant.now().plusSeconds(WINDOW_SECONDS));
            }
            existing.count++;
            return existing;
        });
    }

    public void recordSuccess(String clientKey) {
        failures.remove(normalize(clientKey));
    }

    private static String normalize(String clientKey) {
        return clientKey == null || clientKey.isBlank() ? "unknown" : clientKey.trim();
    }

    private static final class Window {
        int count;
        final Instant expiresAt;

        Window(int count, Instant expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }

        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
