package com.thepourover.cdp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.domain.WebEvent;
import com.thepourover.cdp.repo.SubscriberRepository;
import com.thepourover.cdp.repo.WebEventRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors CSV visitor identity rule: first linked subscriber for a visitor_id wins;
 * later emails on the same visitor do not split the trail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:visitorfirst;DB_CLOSE_DELAY=-1",
        "spring.ai.openai.api-key=test-key",
        "cdp.webhook-secret=secret",
        "cdp.pii-hash-salt=salt",
        "cdp.seed-on-startup=false",
        "cdp.users[0].username=growth",
        "cdp.users[0].password=test",
        "cdp.github-allowed-logins="
})
@Transactional
class VisitorFirstWinsTest {

    @Autowired
    SubscriberRepository subscriberRepository;
    @Autowired
    WebEventRepository webEventRepository;
    @Autowired
    EmailNormalizer emailNormalizer;

    @Test
    void laterEmailOnSameVisitorDoesNotReassign() {
        Subscriber first = newsletter("first@example.com");
        Subscriber second = newsletter("second@example.com");

        WebEvent anon = event("v1", null, "2026-09-28T09:00:00Z");
        webEventRepository.save(anon);

        WebEvent withFirst = event("v1", first.getEmail(), "2026-09-28T10:00:00Z");
        withFirst.setSubscriber(first);
        webEventRepository.save(withFirst);
        webEventRepository.linkAnonymousVisitorEvents("v1", first);

        // Simulate CSV first-wins: visitor already owned by first.
        Subscriber owner = webEventRepository.findByVisitorId("v1").stream()
                .map(WebEvent::getSubscriber)
                .filter(s -> s != null)
                .findFirst()
                .orElseThrow();
        assertEquals(first.getId(), owner.getId());

        WebEvent withSecond = event("v1", second.getEmail(), "2026-09-28T11:00:00Z");
        withSecond.setSubscriber(owner); // keep first owner even though email differs
        webEventRepository.save(withSecond);
        webEventRepository.linkAnonymousVisitorEvents("v1", owner);

        List<WebEvent> all = webEventRepository.findByVisitorId("v1");
        assertEquals(3, all.size());
        assertTrue(all.stream().allMatch(e -> first.getId().equals(e.getSubscriber().getId())));
    }

    @Test
    void gmailAliasesCollapseToSameSubscriberKey() {
        assertEquals(
                emailNormalizer.normalize("a.b+1@gmail.com"),
                emailNormalizer.normalize("ab@googlemail.com"));
    }

    private Subscriber newsletter(String email) {
        Subscriber s = new Subscriber();
        s.setEmail(emailNormalizer.normalize(email));
        s.setStub(false);
        return subscriberRepository.save(s);
    }

    private static WebEvent event(String visitorId, String email, String ts) {
        WebEvent e = new WebEvent();
        e.setVisitorId(visitorId);
        e.setEmail(email);
        e.setPage("/");
        e.setTimestamp(Instant.parse(ts));
        return e;
    }
}
