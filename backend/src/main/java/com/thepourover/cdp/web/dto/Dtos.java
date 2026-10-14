package com.thepourover.cdp.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class Dtos {
    private Dtos() {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(boolean authenticated, String username) {
    }

    public record AuthConfigResponse(boolean githubEnabled, String githubAuthorizationUrl) {
    }

    public record WebEventView(String visitorId, String page, Instant timestamp, String utmSource) {
    }

    public record AppEventView(
            String eventId,
            String event,
            Instant timestamp,
            String deviceId,
            String userId,
            Map<String, Object> properties) {
    }

    public record ProfileResponse(
            Long id,
            String email,
            String status,
            LocalDate signupDate,
            String acquisitionSource,
            LocalDate lastOpenDate,
            boolean stub,
            List<String> userIds,
            List<String> deviceIds,
            List<WebEventView> webEvents,
            List<AppEventView> appEvents) {
    }

    public record AnalyticsMember(
            String subscriberRef,
            String maskedEmail,
            String status,
            String acquisitionSource,
            LocalDate signupDate,
            LocalDate lastOpenDate,
            int engagementScore) {
    }

    /** AI-safe member — no email fields. */
    public record RedactedAnalyticsMember(
            String subscriberRef,
            String status,
            String acquisitionSource,
            LocalDate signupDate,
            LocalDate lastOpenDate,
            int engagementScore) {
    }

    public record AnalyticsMeta(String id, String name, String description, long count) {
    }

    public record AnalyticsResult(
            String id,
            String name,
            String description,
            long count,
            List<AnalyticsMember> members) {
    }

    public record RedactedAnalyticsResult(
            String id,
            String name,
            String description,
            long count,
            List<RedactedAnalyticsMember> members) {
    }

    public record GrowthSnapshot(
            String today,
            long newsletterSubscribers,
            long appUsers,
            long webEventsLinked,
            Map<String, Long> sourceMix,
            long coldInstagram30d,
            long recentWebhookEvents) {
    }

    public record RecentEvent(
            String eventId,
            String event,
            String deviceId,
            String userId,
            Instant timestamp,
            String subscriberRef) {
    }

    public record SnapshotResponse(GrowthSnapshot snapshot, List<RecentEvent> recentEvents) {
    }

    public record ChatRequest(String message, String conversationId, Boolean reset) {
    }

    public record ChatResponse(String answer, String conversationId) {
    }

    public record WebhookPayload(
            String event_id,
            String event,
            String user_id,
            String device_id,
            String timestamp,
            Map<String, Object> properties) {
    }

    public record WebhookResponse(boolean ok, boolean duplicate, Long subscriberId) {
    }

    public record PageCount(String page, long count) {
    }
}
