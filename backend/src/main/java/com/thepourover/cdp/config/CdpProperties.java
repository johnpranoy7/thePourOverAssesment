package com.thepourover.cdp.config;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdp")
public record CdpProperties(
        String webhookSecret,
        String piiHashSalt,
        LocalDate today,
        boolean seedOnStartup,
        List<DemoUser> users,
        /** Comma-separated GitHub logins and/or emails allowed to sign in. Empty = GitHub login off. */
        String githubAllowedLogins
) {
    public CdpProperties {
        if (today == null) {
            today = LocalDate.of(2026, 9, 28);
        }
        if (users == null || users.isEmpty()) {
            users = List.of(
                    new DemoUser("growth", "pour-over"),
                    new DemoUser("recruiter", "demo"));
        }
        if (githubAllowedLogins == null) {
            githubAllowedLogins = "";
        }
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("cdp.webhook-secret / WEBHOOK_SECRET must be set");
        }
        if (piiHashSalt == null || piiHashSalt.isBlank()) {
            throw new IllegalStateException("cdp.pii-hash-salt / PII_HASH_SALT must be set");
        }
    }

    public Set<String> githubAllowlist() {
        if (githubAllowedLogins.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(githubAllowedLogins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean githubLoginEnabled() {
        return !githubAllowlist().isEmpty();
    }

    public record DemoUser(String username, String password) {
        public DemoUser {
            if (username == null) {
                username = "";
            }
            if (password == null) {
                password = "";
            }
        }
    }
}
