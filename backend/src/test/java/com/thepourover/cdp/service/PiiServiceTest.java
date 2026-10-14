package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thepourover.cdp.config.CdpProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PiiServiceTest {

    private final PiiService piiService = new PiiService(
            new CdpProperties("secret", "salt", LocalDate.of(2026, 9, 28), false, List.of(), ""),
            new EmailNormalizer());

    @Test
    void assertNoEmailsRejectsPayloadWithEmail() {
        assertThrows(
                IllegalStateException.class,
                () -> piiService.assertNoEmails(Map.of("email", "a@b.com"), "test"));
    }

    @Test
    void redactsEmailsInChatInput() {
        String redacted = piiService.redactEmailsInText("email me at secret@thepourover.org please");
        assertFalse(piiService.containsEmail(redacted));
        assertTrue(redacted.contains("[REDACTED_EMAIL]"));
    }

    @Test
    void redactsEmailsInAssistantAnswers() {
        String redacted = piiService.redactEmailsInText(
                "The top reader is sam@thepourover.org with score 12");
        assertEquals("The top reader is [REDACTED_EMAIL] with score 12", redacted);
    }

    @Test
    void subscriberRefIsStableAndOpaque() {
        String a = piiService.subscriberRef("  Sam@ThePourOver.ORG ");
        String b = piiService.subscriberRef("sam@thepourover.org");
        assertEquals(a, b);
        assertTrue(a.startsWith("sub_"));
        assertFalse(a.contains("@"));
        assertFalse(a.toLowerCase().contains("sam"));
    }

    @Test
    void maskEmailKeepsDomain() {
        assertEquals("s***@thepourover.org", piiService.maskEmail("Sam@ThePourOver.ORG"));
    }
}
