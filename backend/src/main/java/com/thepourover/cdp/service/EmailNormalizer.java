package com.thepourover.cdp.service;

import org.springframework.stereotype.Component;

/**
 * Sole email normalization entry point for seed, linking, lookup, PII hashing, and stub emails.
 */
@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
