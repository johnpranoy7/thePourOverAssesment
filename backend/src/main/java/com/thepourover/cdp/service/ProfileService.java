package com.thepourover.cdp.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepourover.cdp.domain.*;
import com.thepourover.cdp.repo.*;
import com.thepourover.cdp.web.dto.Dtos.AppEventView;
import com.thepourover.cdp.web.dto.Dtos.ProfileResponse;
import com.thepourover.cdp.web.dto.Dtos.WebEventView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class ProfileService {

    private final SubscriberRepository subscriberRepository;
    private final WebEventRepository webEventRepository;
    private final AppUserRepository appUserRepository;
    private final AppEventRepository appEventRepository;
    private final DeviceLinkRepository deviceLinkRepository;
    private final EmailNormalizer emailNormalizer;
    private final ObjectMapper objectMapper;

    public ProfileService(
            SubscriberRepository subscriberRepository,
            WebEventRepository webEventRepository,
            AppUserRepository appUserRepository,
            AppEventRepository appEventRepository,
            DeviceLinkRepository deviceLinkRepository,
            EmailNormalizer emailNormalizer,
            ObjectMapper objectMapper) {
        this.subscriberRepository = subscriberRepository;
        this.webEventRepository = webEventRepository;
        this.appUserRepository = appUserRepository;
        this.appEventRepository = appEventRepository;
        this.deviceLinkRepository = deviceLinkRepository;
        this.emailNormalizer = emailNormalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProfileResponse findByEmail(String email) {
        String normalized = emailNormalizer.normalize(email);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "email required");
        }
        Subscriber subscriber = subscriberRepository.findByEmail(normalized)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not found"));
        return toProfile(subscriber);
    }

    @Transactional(readOnly = true)
    public Optional<Subscriber> findEntityByEmail(String email) {
        return subscriberRepository.findByEmail(emailNormalizer.normalize(email));
    }

    private ProfileResponse toProfile(Subscriber subscriber) {
        List<WebEvent> webEvents = webEventRepository.findBySubscriberOrderByTimestampAsc(subscriber);
        List<AppEvent> appEvents = appEventRepository.findBySubscriberOrderByTimestampAsc(subscriber);
        List<AppUser> users = appUserRepository.findBySubscriberId(subscriber.getId());

        Set<String> userIds = new LinkedHashSet<>();
        users.forEach(u -> userIds.add(u.getUserId()));
        appEvents.stream().map(AppEvent::getUserId).filter(id -> id != null && !id.isBlank())
                .forEach(userIds::add);

        Set<String> deviceIds = new LinkedHashSet<>();
        appEvents.forEach(e -> deviceIds.add(e.getDeviceId()));
        deviceLinkRepository.findAll().stream()
                .filter(d -> d.getSubscriber() != null && subscriber.getId().equals(d.getSubscriber().getId()))
                .map(DeviceLink::getDeviceId)
                .forEach(deviceIds::add);

        List<WebEventView> webViews = webEvents.stream()
                .map(w -> new WebEventView(w.getVisitorId(), w.getPage(), w.getTimestamp(), w.getUtmSource()))
                .toList();

        List<AppEventView> appViews = new ArrayList<>();
        for (AppEvent e : appEvents) {
            appViews.add(new AppEventView(
                    e.getEventId(),
                    e.getEventType(),
                    e.getTimestamp(),
                    e.getDeviceId(),
                    e.getUserId(),
                    parseProps(e.getPropertiesJson())));
        }

        return new ProfileResponse(
                subscriber.getId(),
                subscriber.getEmail(),
                subscriber.getStatus(),
                subscriber.getSignupDate(),
                subscriber.getAcquisitionSource(),
                subscriber.getLastOpenDate(),
                subscriber.isStub(),
                List.copyOf(userIds),
                List.copyOf(deviceIds),
                webViews,
                appViews);
    }

    private Map<String, Object> parseProps(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            return Map.of("raw", json);
        }
    }
}
