package com.thepourover.cdp.config;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cdp")
public record CdpProperties(
        String uiPassword,
        String webhookSecret,
        String piiHashSalt,
        LocalDate today,
        boolean seedOnStartup
) {
    public CdpProperties {
        // defaults for tests that omit seed-on-startup
        if (today == null) {
            today = LocalDate.of(2026, 9, 28);
        }
    }
}
