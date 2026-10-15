package com.thepourover.cdp.service;

import com.thepourover.cdp.config.CdpProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Service
public class PiiService {

    private static final String REDACTED = "[REDACTED_EMAIL]";

    /** Standard addresses, multi-label domains (co.uk), localhost, and IPv4 hosts. */
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[a-z0-9](?:[a-z0-9._%+-]*[a-z0-9])?@(?:"
                    + "(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,24}"
                    + "|localhost"
                    + "|(?:\\d{1,3}\\.){3}\\d{1,3}"
                    + ")\\b");

    /**
     * Spaced / (at) / [at] obfuscations that still clearly mean an email.
     * Applied before the strict pattern so the local/domain parts stay intact.
     */
    private static final Pattern EMAIL_OBFUSCATED = Pattern.compile(
            "(?i)\\b[a-z0-9][a-z0-9._%+-]*\\s*(?:\\(at\\)|\\[at\\]|@)\\s*"
                    + "[a-z0-9][a-z0-9.-]*\\.[a-z]{2,24}\\b");

    /**
     * Catch-all for any remaining local@host shapes the strict pattern missed
     * (e.g. trailing-dot locals, odd hosts). Prefer false-positive redaction over a leak.
     */
    private static final Pattern EMAIL_LOOSE = Pattern.compile(
            "(?i)[a-z0-9][a-z0-9._%+-]*@[a-z0-9][a-z0-9.-]*[a-z0-9]");

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

    /** Sanitize free text before it is sent to or returned from the model. */
    public String redactEmailsInText(String text) {
        if (text == null) {
            return null;
        }
        String out = EMAIL_OBFUSCATED.matcher(text).replaceAll(REDACTED);
        out = EMAIL.matcher(out).replaceAll(REDACTED);
        out = EMAIL_LOOSE.matcher(out).replaceAll(REDACTED);
        return out;
    }

    public void assertNoEmails(Object payload, String context) {
        String json = String.valueOf(payload);
        if (containsEmail(json)) {
            throw new IllegalStateException("PII leak blocked in " + context + ": email pattern found");
        }
    }

    public boolean containsEmail(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return EMAIL_OBFUSCATED.matcher(text).find()
                || EMAIL.matcher(text).find()
                || EMAIL_LOOSE.matcher(text).find();
    }
}
