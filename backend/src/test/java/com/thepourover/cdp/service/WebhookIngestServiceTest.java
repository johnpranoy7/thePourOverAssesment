package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        "cdp.ui-password=test",
        "cdp.webhook-secret=secret",
        "cdp.pii-hash-salt=salt"
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

    @Test
    void dedupesByEventId() {
        WebhookPayload payload = new WebhookPayload(
                "evt_1", "app_open", null, "d_1", "2026-09-28T10:00:00Z", Map.of());
        assertFalse(webhookIngestService.ingest(payload).duplicate());
        assertTrue(webhookIngestService.ingest(payload).duplicate());
    }

    @Test
    void attachesAnonymousEventsOnLogin() {
        Subscriber sub = new Subscriber();
        sub.setEmail("reader@example.com");
        sub.setStub(false);
        subscriberRepository.save(sub);
        AppUser user = new AppUser();
        user.setUserId("u_known");
        user.setEmail("reader@example.com");
        user.setSubscriber(sub);
        appUserRepository.save(user);

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
    void unknownUserIdGetsStubProfile() {
        var result = webhookIngestService.ingest(new WebhookPayload(
                "evt_stub", "login", "u_never", "d_2", "2026-09-28T11:00:00Z", Map.of()));
        assertFalse(result.duplicate());
        assertTrue(subscriberRepository.findByEmail("stub+u_never@cdp.local").isPresent());
        assertTrue(subscriberRepository.findByEmail("stub+u_never@cdp.local").get().isStub());
    }
}
