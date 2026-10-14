package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thepourover.cdp.domain.AppEvent;
import com.thepourover.cdp.domain.AppUser;
import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.repo.AppEventRepository;
import com.thepourover.cdp.web.dto.Dtos.WebhookPayload;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:webhooktest;DB_CLOSE_DELAY=-1",
        "spring.ai.openai.api-key=test-key",
        "cdp.webhook-secret=secret",
        "cdp.pii-hash-salt=salt",
        "cdp.users[0].username=growth",
        "cdp.users[0].password=test",
        "cdp.github-allowed-logins="
})
@Transactional
class WebhookIngestServiceTest {

    @Autowired
    WebhookIngestService webhookIngestService;
    @Autowired
    AppEventRepository appEventRepository;
    @Autowired
    com.thepourover.cdp.repo.SubscriberRepository subscriberRepository;
    @Autowired
    com.thepourover.cdp.repo.AppUserRepository appUserRepository;
    @Autowired
    com.thepourover.cdp.repo.DeviceLinkRepository deviceLinkRepository;

    @Test
    void dedupesByEventId() {
        Subscriber sub = seedKnownUser("u_dedupe", "dedupe@example.com");
        WebhookPayload payload = new WebhookPayload(
                "evt_1", "app_open", "u_dedupe", "d_1", "2026-09-28T10:00:00Z", Map.of());
        var first = webhookIngestService.ingest(payload);
        assertFalse(first.duplicate());
        assertEquals(sub.getId(), first.subscriberId());
        var second = webhookIngestService.ingest(payload);
        assertTrue(second.duplicate());
        assertEquals(sub.getId(), second.subscriberId());
    }

    @Test
    void sharedDeviceKeepsFirstDeviceLink() {
        Subscriber first = seedKnownUser("u_a", "a@example.com");
        Subscriber second = seedKnownUser("u_b", "b@example.com");

        webhookIngestService.ingest(new WebhookPayload(
                "evt_a", "login", "u_a", "d_shared", "2026-09-28T10:00:00Z", Map.of()));
        webhookIngestService.ingest(new WebhookPayload(
                "evt_b", "login", "u_b", "d_shared", "2026-09-28T11:00:00Z", Map.of()));
        webhookIngestService.ingest(new WebhookPayload(
                "evt_anon", "app_open", null, "d_shared", "2026-09-28T12:00:00Z", Map.of()));

        assertEquals(first.getId(),
                deviceLinkRepository.findById("d_shared").orElseThrow().getSubscriber().getId());
        assertEquals(first.getId(),
                appEventRepository.findByEventId("evt_anon").orElseThrow().getSubscriber().getId());
        assertEquals(second.getId(),
                appEventRepository.findByEventId("evt_b").orElseThrow().getSubscriber().getId());
    }

    @Test
    void upgradesStubWhenAppUserEmailMatchesNewsletterSubscriber() {
        Subscriber real = new Subscriber();
        real.setEmail("real@example.com");
        real.setStub(false);
        subscriberRepository.save(real);

        webhookIngestService.ingest(new WebhookPayload(
                "evt_stub_first", "login", "u_upgrade", "d_up", "2026-09-28T10:00:00Z", Map.of()));
        assertTrue(subscriberRepository.findByEmail("stub+u_upgrade@cdp.local").orElseThrow().isStub());

        AppUser user = appUserRepository.findById("u_upgrade").orElseThrow();
        user.setEmail("real@example.com");
        appUserRepository.save(user);

        var result = webhookIngestService.ingest(new WebhookPayload(
                "evt_stub_second", "app_open", "u_upgrade", "d_up", "2026-09-28T11:00:00Z", Map.of()));
        assertEquals(real.getId(), result.subscriberId());
        assertFalse(appUserRepository.findById("u_upgrade").orElseThrow().isStub());
    }

    @Test
    void attachesAnonymousEventsOnLogin() {
        Subscriber sub = seedKnownUser("u_known", "reader@example.com");

        webhookIngestService.ingest(new WebhookPayload(
                "evt_anon", "app_open", null, "d_merge", "2026-09-28T09:00:00Z", Map.of()));
        webhookIngestService.ingest(new WebhookPayload(
                "evt_login", "login", "u_known", "d_merge", "2026-09-28T12:00:00Z", Map.of()));

        List<AppEvent> events = appEventRepository.findBySubscriberOrderByTimestampAsc(sub);
        assertEquals(2, events.size());
        assertEquals("evt_anon", events.get(0).getEventId());
        assertEquals("evt_login", events.get(1).getEventId());
    }

    @Test
    void identifiedThenAnonymousUsesDeviceLink() {
        Subscriber sub = seedKnownUser("u_first", "first@example.com");

        webhookIngestService.ingest(new WebhookPayload(
                "evt_id", "app_open", "u_first", "d_order", "2026-09-28T12:00:00Z", Map.of()));
        webhookIngestService.ingest(new WebhookPayload(
                "evt_later_anon", "app_open", null, "d_order", "2026-09-28T08:00:00Z", Map.of()));

        List<AppEvent> events = appEventRepository.findBySubscriberOrderByTimestampAsc(sub);
        assertEquals(2, events.size());
        assertNotNull(deviceLinkRepository.findById("d_order").orElseThrow().getSubscriber());
    }

    @Test
    void lateArrivingLoginStillAttachesEarlierAnonymous() {
        Subscriber sub = seedKnownUser("u_late", "late@example.com");

        webhookIngestService.ingest(new WebhookPayload(
                "evt_early_anon", "read_story", null, "d_late", "2026-09-28T15:00:00Z", Map.of()));
        // Login timestamp is earlier than the anon event — identity attach is not time-ordered.
        webhookIngestService.ingest(new WebhookPayload(
                "evt_login_earlier", "login", "u_late", "d_late", "2026-09-28T10:00:00Z", Map.of()));

        List<AppEvent> events = appEventRepository.findBySubscriberOrderByTimestampAsc(sub);
        assertEquals(2, events.size());
    }

    @Test
    void unknownUserIdGetsStubProfile() {
        var result = webhookIngestService.ingest(new WebhookPayload(
                "evt_stub", "login", "u_never", "d_2", "2026-09-28T11:00:00Z", Map.of()));
        assertFalse(result.duplicate());
        assertTrue(subscriberRepository.findByEmail("stub+u_never@cdp.local").isPresent());
        assertTrue(subscriberRepository.findByEmail("stub+u_never@cdp.local").get().isStub());
        assertNotNull(result.subscriberId());
    }

    @Test
    void anyEventWithUserIdMergesNotOnlyLogin() {
        Subscriber sub = seedKnownUser("u_open", "open@example.com");
        webhookIngestService.ingest(new WebhookPayload(
                "evt_a", "app_open", null, "d_any", "2026-09-28T09:00:00Z", Map.of()));
        webhookIngestService.ingest(new WebhookPayload(
                "evt_b", "app_open", "u_open", "d_any", "2026-09-28T10:00:00Z", Map.of()));
        assertEquals(2, appEventRepository.findBySubscriberOrderByTimestampAsc(sub).size());
    }

    @Test
    void anonymousWithoutPriorLinkStaysUnlinked() {
        webhookIngestService.ingest(new WebhookPayload(
                "evt_solo", "app_open", null, "d_solo", "2026-09-28T10:00:00Z", Map.of()));
        AppEvent event = appEventRepository.findByEventId("evt_solo").orElseThrow();
        assertNull(event.getSubscriber());
        assertTrue(deviceLinkRepository.findById("d_solo").isEmpty());
    }

    private Subscriber seedKnownUser(String userId, String email) {
        Subscriber sub = new Subscriber();
        sub.setEmail(email);
        sub.setStub(false);
        subscriberRepository.save(sub);
        AppUser user = new AppUser();
        user.setUserId(userId);
        user.setEmail(email);
        user.setSubscriber(sub);
        appUserRepository.save(user);
        return sub;
    }
}
