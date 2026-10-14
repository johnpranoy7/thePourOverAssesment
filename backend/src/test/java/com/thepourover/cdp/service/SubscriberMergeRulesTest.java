package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thepourover.cdp.domain.Subscriber;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SubscriberMergeRulesTest {

    @Test
    void prefersActiveStatus() {
        Subscriber unsub = new Subscriber();
        unsub.setStatus("unsubscribed");
        unsub.setSignupDate(LocalDate.of(2026, 1, 1));
        Subscriber active = new Subscriber();
        active.setStatus("active");
        active.setSignupDate(LocalDate.of(2025, 1, 1));
        assertEquals("active", SubscriberMergeRules.pickWinner(unsub, active).getStatus());
    }
}
