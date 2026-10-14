package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.thepourover.cdp.domain.Subscriber;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubscriberMergeRulesTest {

    @Test
    void prefersActiveStatus() {
        Subscriber unsub = subscriber("unsubscribed", LocalDate.of(2026, 1, 1), null, null);
        Subscriber active = subscriber("active", LocalDate.of(2025, 1, 1), null, null);
        assertEquals("active", SubscriberMergeRules.pickWinner(unsub, active).getStatus());
    }

    @Test
    void prefersLaterSignupWhenStatusTied() {
        Subscriber older = subscriber("active", LocalDate.of(2025, 1, 1), null, null);
        Subscriber newer = subscriber("active", LocalDate.of(2026, 6, 1), null, null);
        assertSame(newer, SubscriberMergeRules.pickWinner(older, newer));
    }

    @Test
    void prefersNonNullSignupOverNull() {
        Subscriber withSignup = subscriber("active", LocalDate.of(2026, 1, 1), null, null);
        Subscriber without = subscriber("active", null, null, null);
        assertSame(withSignup, SubscriberMergeRules.pickWinner(without, withSignup));
    }

    @Test
    void prefersNonNullLastOpenWhenOtherwiseTied() {
        Subscriber cold = subscriber("active", LocalDate.of(2026, 1, 1), null, "instagram");
        Subscriber opened = subscriber("active", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 1), "instagram");
        assertSame(opened, SubscriberMergeRules.pickWinner(cold, opened));
    }

    @Test
    void prefersAcquisitionSourceThenKeepsFirstOnFullTie() {
        Subscriber withSource = subscriber("active", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "web");
        Subscriber blankSource = subscriber("active", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), null);
        assertSame(withSource, SubscriberMergeRules.pickWinner(blankSource, withSource));

        Subscriber a = subscriber("active", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "web");
        Subscriber b = subscriber("active", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), "web");
        assertSame(a, SubscriberMergeRules.pickWinner(a, b));
    }

    private static Subscriber subscriber(
            String status, LocalDate signup, LocalDate lastOpen, String source) {
        Subscriber s = new Subscriber();
        s.setStatus(status);
        s.setSignupDate(signup);
        s.setLastOpenDate(lastOpen);
        s.setAcquisitionSource(source);
        return s;
    }
}
