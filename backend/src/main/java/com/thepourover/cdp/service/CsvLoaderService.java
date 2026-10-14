package com.thepourover.cdp.service;

import com.thepourover.cdp.domain.AppUser;
import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.domain.WebEvent;
import com.thepourover.cdp.repo.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Service
public class CsvLoaderService {

    private static final Logger log = LoggerFactory.getLogger(CsvLoaderService.class);

    private final SubscriberRepository subscriberRepository;
    private final WebEventRepository webEventRepository;
    private final AppUserRepository appUserRepository;
    private final AppEventRepository appEventRepository;
    private final DeviceLinkRepository deviceLinkRepository;
    private final EmailNormalizer emailNormalizer;

    public CsvLoaderService(
            SubscriberRepository subscriberRepository,
            WebEventRepository webEventRepository,
            AppUserRepository appUserRepository,
            AppEventRepository appEventRepository,
            DeviceLinkRepository deviceLinkRepository,
            EmailNormalizer emailNormalizer) {
        this.subscriberRepository = subscriberRepository;
        this.webEventRepository = webEventRepository;
        this.appUserRepository = appUserRepository;
        this.appEventRepository = appEventRepository;
        this.deviceLinkRepository = deviceLinkRepository;
        this.emailNormalizer = emailNormalizer;
    }

    public boolean isSeeded() {
        return subscriberRepository.count() > 0;
    }

    @Transactional
    public Map<String, Object> seed(boolean force) {
        if (isSeeded() && !force) {
            return stats("skipped");
        }
        if (force) {
            appEventRepository.deleteAllInBatch();
            deviceLinkRepository.deleteAllInBatch();
            webEventRepository.deleteAllInBatch();
            appUserRepository.deleteAllInBatch();
            subscriberRepository.deleteAllInBatch();
        }

        int subscribers = loadSubscribers();
        int appUsers = loadAppUsers();
        int webEvents = loadWebEvents();
        log.info("CSV seed complete: subscribers={}, appUsers={}, webEvents={}", subscribers, appUsers, webEvents);
        Map<String, Object> result = stats("loaded");
        result.put("subscribers_rows", subscribers);
        result.put("app_users_rows", appUsers);
        result.put("web_events_rows", webEvents);
        return result;
    }

    private Map<String, Object> stats(String status) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("subscribers", subscriberRepository.countByStubFalse());
        m.put("app_users", appUserRepository.count());
        m.put("web_events", webEventRepository.count());
        m.put("web_events_linked", webEventRepository.countBySubscriberIsNotNull());
        return m;
    }

    private int loadSubscribers() {
        Map<String, Subscriber> winners = new HashMap<>();
        try (Reader reader = open("data/subscribers.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String email = emailNormalizer.normalize(row.get("email"));
                if (email == null) {
                    continue;
                }
                Subscriber candidate = new Subscriber();
                candidate.setEmail(email);
                candidate.setStatus(emptyToNull(row.get("status")));
                candidate.setSignupDate(parseDate(row.get("signup_date")));
                candidate.setAcquisitionSource(emptyToNull(row.get("acquisition_source")));
                candidate.setLastOpenDate(parseDate(row.get("last_open_date")));
                candidate.setStub(false);

                Subscriber existing = winners.get(email);
                if (existing == null) {
                    winners.put(email, candidate);
                } else {
                    winners.put(email, SubscriberMergeRules.pickWinner(existing, candidate));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load subscribers.csv", e);
        }
        subscriberRepository.saveAll(winners.values());
        return winners.size();
    }

    private int loadAppUsers() {
        int count = 0;
        try (Reader reader = open("data/app_users.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String userId = emptyToNull(row.get("user_id"));
                if (userId == null) {
                    continue;
                }
                String email = emailNormalizer.normalize(row.get("email"));
                AppUser user = new AppUser();
                user.setUserId(userId);
                user.setEmail(email);
                user.setCreatedDate(parseDate(row.get("created_at")));
                user.setStub(false);
                if (email != null) {
                    subscriberRepository.findByEmail(email).ifPresentOrElse(user::setSubscriber, () -> {
                        Subscriber s = new Subscriber();
                        s.setEmail(email);
                        s.setStub(false);
                        s.setSignupDate(user.getCreatedDate());
                        user.setSubscriber(subscriberRepository.save(s));
                    });
                }
                appUserRepository.save(user);
                count++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load app_users.csv", e);
        }
        return count;
    }

    private int loadWebEvents() {
        int count = 0;
        try (Reader reader = open("data/web_events.csv");
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                     .setIgnoreEmptyLines(true).setTrim(true).build().parse(reader)) {
            for (CSVRecord row : parser) {
                String visitorId = emptyToNull(row.get("visitor_id"));
                if (visitorId == null) {
                    continue;
                }
                String email = emailNormalizer.normalize(emptyToNull(row.get("email")));
                WebEvent event = new WebEvent();
                event.setVisitorId(visitorId);
                event.setPage(emptyToNull(row.get("page")));
                event.setTimestamp(parseInstant(row.get("timestamp")));
                event.setUtmSource(emptyToNull(row.get("utm_source")));
                event.setEmail(email);

                Subscriber subscriber = null;
                if (email != null) {
                    subscriber = subscriberRepository.findByEmail(email).orElseGet(() -> {
                        Subscriber s = new Subscriber();
                        s.setEmail(email);
                        s.setStub(false);
                        return subscriberRepository.save(s);
                    });
                    event.setSubscriber(subscriber);
                }
                webEventRepository.save(event);
                if (subscriber != null) {
                    webEventRepository.linkAnonymousVisitorEvents(visitorId, subscriber);
                }
                count++;
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load web_events.csv", e);
        }
        return count;
    }

    private Reader open(String classpath) throws Exception {
        return new InputStreamReader(new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8);
    }

    private static String emptyToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(value.substring(0, 10)).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            } catch (Exception ex) {
                return Instant.EPOCH;
            }
        }
    }
}
