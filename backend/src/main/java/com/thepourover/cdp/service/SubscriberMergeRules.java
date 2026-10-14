package com.thepourover.cdp.service;

import com.thepourover.cdp.domain.Subscriber;

import java.time.LocalDate;

/** Pure rules for subscriber dedupe — prefer active, then latest signup, then completeness. */
public final class SubscriberMergeRules {

    private SubscriberMergeRules() {
    }

    public static Subscriber pickWinner(Subscriber a, Subscriber b) {
        int status = Integer.compare(statusRank(a.getStatus()), statusRank(b.getStatus()));
        if (status != 0) {
            return status > 0 ? a : b;
        }
        LocalDate sa = a.getSignupDate();
        LocalDate sb = b.getSignupDate();
        if (sa != null && sb != null && !sa.equals(sb)) {
            return sa.isAfter(sb) ? a : b;
        }
        if (sa != null && sb == null) {
            return a;
        }
        if (sa == null && sb != null) {
            return b;
        }
        int open = Boolean.compare(a.getLastOpenDate() != null, b.getLastOpenDate() != null);
        if (open != 0) {
            return open > 0 ? a : b;
        }
        int src = Boolean.compare(
                a.getAcquisitionSource() != null && !a.getAcquisitionSource().isBlank(),
                b.getAcquisitionSource() != null && !b.getAcquisitionSource().isBlank());
        if (src != 0) {
            return src > 0 ? a : b;
        }
        // Full tie: keep the first argument (stable for seed iteration).
        return a;
    }

    private static int statusRank(String status) {
        if (status != null && status.equalsIgnoreCase("active")) {
            return 2;
        }
        return 1;
    }
}
