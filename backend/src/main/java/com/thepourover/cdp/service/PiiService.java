package com.thepourover.cdp.service;

import com.thepourover.cdp.config.CdpProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
public class PiiService {

    /** Word-bounded; allows multi-label domains (e.g. co.uk). */
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9](?:[a-z0-9._%+-]*[a-z0-9])?@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,24}\\b");

    private final CdpProperties properties;
    private final EmailNormalizer emailNormalizer;

    public PiiService(CdpProperties properties, EmailNormalizer emailNormalizer) {
        this.properties = properties;
        this.emailNormalizer = emailNormalizer;
    }

    public String subscriberRef(String email) {
        String normalized = emailNormalizer.normalize(email);
        if (normalized == null) {
            return "ref_unknown";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.piiHashSalt().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sub_");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash email", e);
        }
    }

    public String maskEmail(String email) {
        String normalized = emailNormalizer.normalize(email);
        if (normalized == null) {
            return "(none)";
        }
        int at = normalized.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return normalized.charAt(0) + "***@" + normalized.substring(at + 1);
    }

    public String redactEmailsInText(String text) {
        if (text == null) {
            return null;
        }
        return EMAIL.matcher(text).replaceAll("[REDACTED_EMAIL]");
    }

    public void assertNoEmails(Object payload, String context) {
        String json = String.valueOf(payload);
        if (EMAIL.matcher(json).find()) {
            throw new IllegalStateException("PII leak blocked in " + context + ": email pattern found");
        }
    }

    public boolean containsEmail(String text) {
        return text != null && EMAIL.matcher(text).find();
    }
}
