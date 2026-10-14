package com.thepourover.cdp.service;

import org.springframework.stereotype.Component;

/**
 * Sole email normalization entry point for seed, linking, lookup, and webhooks.
 */
@Component
public class EmailNormalizer {

    public String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }
}
