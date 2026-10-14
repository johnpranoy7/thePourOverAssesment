package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    private final EmailNormalizer normalizer = new EmailNormalizer();

    @Test
    void trimsAndLowercases() {
        assertEquals("sam@thepourover.org", normalizer.normalize("  Sam@ThePourOver.ORG "));
    }

    @Test
    void emptyReturnsNull() {
        assertNull(normalizer.normalize(""));
        assertNull(normalizer.normalize("   "));
        assertNull(normalizer.normalize(null));
    }

    @Test
    void gmailStripsPlusAndDots() {
        assertEquals("janesmith@gmail.com", normalizer.normalize("Jane.Smith+news@gmail.com"));
        assertEquals("janesmith@gmail.com", normalizer.normalize("jane.smith@googlemail.com"));
    }

    @Test
    void nonGmailKeepsDotsAndPlus() {
        assertEquals("jane.smith+news@thepourover.org",
                normalizer.normalize("Jane.Smith+news@ThePourOver.org"));
    }
}
