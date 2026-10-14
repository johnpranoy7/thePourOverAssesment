package com.thepourover.cdp.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * Sole email normalization entry point for seed, linking, lookup, PII hashing, and stub emails.
 * Gmail / Googlemail: strip {@code +tags}, remove dots in the local part, canonicalize domain.
 */
@Component
public class EmailNormalizer {

    private static final Set<String> GMAIL_DOMAINS = Set.of("gmail.com", "googlemail.com");

    public String normalize(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        int at = normalized.lastIndexOf('@');
        if (at <= 0 || at == normalized.length() - 1) {
            return normalized;
        }
        String local = normalized.substring(0, at);
        String domain = normalized.substring(at + 1);
        if (GMAIL_DOMAINS.contains(domain)) {
            int plus = local.indexOf('+');
            if (plus >= 0) {
                local = local.substring(0, plus);
            }
            local = local.replace(".", "");
            domain = "gmail.com";
            if (local.isEmpty()) {
                return null;
            }
            return local + "@" + domain;
        }
        return local + "@" + domain;
    }
}
