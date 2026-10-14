package com.thepourover.cdp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepourover.cdp.domain.AppEvent;
import com.thepourover.cdp.domain.AppUser;
import com.thepourover.cdp.domain.DeviceLink;
import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.repo.AppEventRepository;
import com.thepourover.cdp.repo.AppUserRepository;
import com.thepourover.cdp.repo.DeviceLinkRepository;
import com.thepourover.cdp.repo.SubscriberRepository;
import com.thepourover.cdp.web.dto.Dtos.WebhookPayload;
import com.thepourover.cdp.web.dto.Dtos.WebhookResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class WebhookIngestService {

    private static final Set<String> ALLOWED = Set.of("app_open", "read_story", "link_click", "login");

    private final AppEventRepository appEventRepository;
    private final AppUserRepository appUserRepository;
    private final SubscriberRepository subscriberRepository;
    private final DeviceLinkRepository deviceLinkRepository;
    private final EmailNormalizer emailNormalizer;
    private final ObjectMapper objectMapper;

    public WebhookIngestService(
            AppEventRepository appEventRepository,
            AppUserRepository appUserRepository,
            SubscriberRepository subscriberRepository,
            DeviceLinkRepository deviceLinkRepository,
            EmailNormalizer emailNormalizer,
            ObjectMapper objectMapper) {
        this.appEventRepository = appEventRepository;
        this.appUserRepository = appUserRepository;
        this.subscriberRepository = subscriberRepository;
        this.deviceLinkRepository = deviceLinkRepository;
        this.emailNormalizer = emailNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookResponse ingest(WebhookPayload payload) {
        validate(payload);
        if (appEventRepository.existsByEventId(payload.event_id())) {
            return new WebhookResponse(true, true, null);
        }

        Subscriber subscriber = null;
        String userId = blankToNull(payload.user_id());
        String deviceId = payload.device_id();

        if (userId != null) {
            subscriber = resolveUser(userId);
            mergeAnonymousDeviceEvents(deviceId, subscriber);
            upsertDeviceLink(deviceId, subscriber);
        } else {
            DeviceLink link = deviceLinkRepository.findById(deviceId).orElse(null);
            if (link != null) {
                subscriber = link.getSubscriber();
            }
        }

        AppEvent event = new AppEvent();
        event.setEventId(payload.event_id());
        event.setEventType(payload.event());
        event.setUserId(userId);
        event.setDeviceId(deviceId);
        event.setTimestamp(parseTimestamp(payload.timestamp()));
        event.setPropertiesJson(writeProps(payload.properties()));
        event.setSubscriber(subscriber);
        appEventRepository.save(event);

        return new WebhookResponse(true, false, subscriber == null ? null : subscriber.getId());
    }

    private void validate(WebhookPayload payload) {
        if (payload == null
                || blankToNull(payload.event_id()) == null
                || blankToNull(payload.event()) == null
                || blankToNull(payload.device_id()) == null
                || blankToNull(payload.timestamp()) == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payload");
        }
        if (!ALLOWED.contains(payload.event())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported event type");
        }
    }

    private Subscriber resolveUser(String userId) {
        return appUserRepository.findById(userId).map(AppUser::getSubscriber).map(s -> {
            if (s != null) {
                return s;
            }
            return createStubForUser(userId);
        }).orElseGet(() -> createStubForUser(userId));
    }

    private Subscriber createStubForUser(String userId) {
        String stubEmail = emailNormalizer.normalize("stub+" + userId + "@cdp.local");
        if (stubEmail == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid user_id");
        }
        Subscriber subscriber = subscriberRepository.findByEmail(stubEmail).orElseGet(() -> {
            Subscriber s = new Subscriber();
            s.setEmail(stubEmail);
            s.setStub(true);
            s.setStatus("unknown");
            return subscriberRepository.save(s);
        });

        AppUser user = appUserRepository.findById(userId).orElseGet(AppUser::new);
        user.setUserId(userId);
        user.setStub(true);
        user.setSubscriber(subscriber);
        if (user.getEmail() == null) {
            user.setEmail(stubEmail);
        }
        appUserRepository.save(user);
        return subscriber;
    }

    private void mergeAnonymousDeviceEvents(String deviceId, Subscriber subscriber) {
        appEventRepository.attachDeviceEvents(deviceId, subscriber);
    }

    private void upsertDeviceLink(String deviceId, Subscriber subscriber) {
        DeviceLink link = deviceLinkRepository.findById(deviceId).orElseGet(DeviceLink::new);
        link.setDeviceId(deviceId);
        link.setSubscriber(subscriber);
        deviceLinkRepository.save(link);
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timestamp");
        }
    }

    private String writeProps(Map<String, Object> props) {
        try {
            return objectMapper.writeValueAsString(props == null ? Map.of() : props);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }
}
