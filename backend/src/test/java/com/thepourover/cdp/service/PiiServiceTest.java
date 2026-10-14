package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thepourover.cdp.config.CdpProperties;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiServiceTest {

    private final PiiService piiService = new PiiService(
            new CdpProperties("x", "y", "salt", LocalDate.of(2026, 9, 28), false),
            new EmailNormalizer());

    @Test
    void assertNoEmailsBlocksLeaks() {
        assertThrows(IllegalStateException.class,
                () -> piiService.assertNoEmails(Map.of("email", "a@b.com"), "test"));
    }

    @Test
    void redactsEmailsInChatInput() {
        String redacted = piiService.redactEmailsInText("email me at secret@thepourover.org please");
        assertFalse(piiService.containsEmail(redacted));
        assertTrue(redacted.contains("[REDACTED_EMAIL]"));
    }
}
