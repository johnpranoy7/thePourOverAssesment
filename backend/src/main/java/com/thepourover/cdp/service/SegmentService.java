package com.thepourover.cdp.service;

import com.thepourover.cdp.config.CdpProperties;
import com.thepourover.cdp.domain.AppEvent;
import com.thepourover.cdp.domain.Subscriber;
import com.thepourover.cdp.domain.WebEvent;
import com.thepourover.cdp.repo.AppEventRepository;
import com.thepourover.cdp.repo.AppUserRepository;
import com.thepourover.cdp.repo.SubscriberRepository;
import com.thepourover.cdp.repo.WebEventRepository;
import com.thepourover.cdp.web.dto.Dtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;

@Service
public class SegmentService {

    public record SegmentDef(String id, String name, String description, Predicate<Subscriber> filter,
                             boolean sortByEngagement) {
    }

    private final SubscriberRepository subscriberRepository;
    private final WebEventRepository webEventRepository;
    private final AppEventRepository appEventRepository;
    private final AppUserRepository appUserRepository;
    private final PiiService piiService;
    private final CdpProperties properties;

    public SegmentService(
            SubscriberRepository subscriberRepository,
            WebEventRepository webEventRepository,
            AppEventRepository appEventRepository,
            AppUserRepository appUserRepository,
            PiiService piiService,
            CdpProperties properties) {
        this.subscriberRepository = subscriberRepository;
        this.webEventRepository = webEventRepository;
        this.appEventRepository = appEventRepository;
        this.appUserRepository = appUserRepository;
        this.piiService = piiService;
        this.properties = properties;
    }

    private List<SegmentDef> defs() {
        LocalDate today = properties.today();
        LocalDate coldCutoff = today.minusDays(30);
        return List.of(
                new SegmentDef(
                        "instagram-cold-30d",
                        "Instagram signups cold 30d",
                        "Signed up from Instagram and have not opened in 30 days (as of " + today + ").",
                        s -> !s.isStub()
                                && "instagram".equalsIgnoreCase(nullToEmpty(s.getAcquisitionSource()))
                                && isCold(s, coldCutoff),
                        false),
                new SegmentDef(
                        "most-engaged",
                        "Most engaged readers",
                        "Newsletter profiles ranked by opens, story reads, app opens, and site visits.",
                        s -> !s.isStub() && engagementScore(s) > 0,
                        true),
                new SegmentDef(
                        "new-subscriber-first-pages",
                        "New subscribers — first pages",
                        "Subscribers who signed up in the last 30 days and have at least one web visit.",
                        s -> !s.isStub()
                                && s.getSignupDate() != null
                                && !s.getSignupDate().isBefore(today.minusDays(30))
                                && !webEventRepository.findBySubscriberOrderByTimestampAsc(s).isEmpty(),
                        false)
        );
    }

    @Transactional(readOnly = true)
    public List<SegmentMeta> listSegments() {
        List<SegmentMeta> out = new ArrayList<>();
        for (SegmentDef def : defs()) {
            long count = subscriberRepository.findByStubFalse().stream().filter(def.filter()).count();
            out.add(new SegmentMeta(def.id(), def.name(), def.description(), count));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SegmentResult runSegment(String id, int limit) {
        SegmentDef def = defs().stream().filter(d -> d.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown segment: " + id));
        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(def.filter())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (def.sortByEngagement()) {
            matched.sort(Comparator.comparingInt(this::engagementScore).reversed());
        }
        List<SegmentMember> members = matched.stream().limit(limit).map(this::toMember).toList();
        return new SegmentResult(def.id(), def.name(), def.description(), matched.size(), members);
    }

    @Transactional(readOnly = true)
    public RedactedSegmentResult runSegmentRedacted(String id, int limit) {
        SegmentResult result = runSegment(id, limit);
        List<RedactedSegmentMember> members = result.members().stream()
                .map(m -> new RedactedSegmentMember(
                        m.subscriberRef(),
                        m.status(),
                        m.acquisitionSource(),
                        m.signupDate(),
                        m.lastOpenDate(),
                        m.engagementScore()))
                .toList();
        RedactedSegmentResult redacted = new RedactedSegmentResult(
                result.id(), result.name(), result.description(), result.count(), members);
        piiService.assertNoEmails(redacted, "runSegmentRedacted");
        return redacted;
    }

    @Transactional(readOnly = true)
    public SegmentResult customSegment(String source, Integer inactiveDays, int limit) {
        LocalDate today = properties.today();
        LocalDate cutoff = inactiveDays == null ? null : today.minusDays(inactiveDays);
        String src = source == null ? null : source.toLowerCase(Locale.ROOT);
        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(s -> src == null || src.equalsIgnoreCase(nullToEmpty(s.getAcquisitionSource())))
                .filter(s -> cutoff == null || isCold(s, cutoff))
                .toList();
        List<SegmentMember> members = matched.stream().limit(limit).map(this::toMember).toList();
        return new SegmentResult(
                "custom",
                "Custom filter",
                "source=" + (source == null ? "any" : source) + ", inactive_days="
                        + (inactiveDays == null ? "any" : inactiveDays),
                matched.size(),
                members);
    }

    @Transactional(readOnly = true)
    public RedactedSegmentResult customSegmentRedacted(String source, Integer inactiveDays, int limit) {
        SegmentResult result = customSegment(source, inactiveDays, limit);
        List<RedactedSegmentMember> members = result.members().stream()
                .map(m -> new RedactedSegmentMember(
                        m.subscriberRef(), m.status(), m.acquisitionSource(),
                        m.signupDate(), m.lastOpenDate(), m.engagementScore()))
                .toList();
        RedactedSegmentResult redacted = new RedactedSegmentResult(
                result.id(), result.name(), result.description(), result.count(), members);
        piiService.assertNoEmails(redacted, "customSegmentRedacted");
        return redacted;
    }

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot() {
        Map<String, Long> mix = new LinkedHashMap<>();
        for (Object[] row : subscriberRepository.countByAcquisitionSource()) {
            String key = row[0] == null || String.valueOf(row[0]).isBlank() ? "unknown" : String.valueOf(row[0]).toLowerCase(Locale.ROOT);
            mix.put(key, ((Number) row[1]).longValue());
        }
        long cold = runSegment("instagram-cold-30d", 0).count();
        List<RecentEvent> recent = appEventRepository.findTop50ByOrderByTimestampDesc().stream()
                .limit(20)
                .map(e -> new RecentEvent(
                        e.getEventId(),
                        e.getEventType(),
                        e.getDeviceId(),
                        e.getUserId(),
                        e.getTimestamp(),
                        e.getSubscriber() == null
                                ? "anon"
                                : piiService.subscriberRef(e.getSubscriber().getEmail())))
                .toList();

        GrowthSnapshot snap = new GrowthSnapshot(
                properties.today().toString(),
                subscriberRepository.countByStubFalse(),
                appUserRepository.count(),
                webEventRepository.countBySubscriberIsNotNull(),
                mix,
                cold,
                recent.size());
        return new SnapshotResponse(snap, recent);
    }

    @Transactional(readOnly = true)
    public List<PageCount> topPagesForNewSubscribers(int limit) {
        LocalDate cutoff = properties.today().minusDays(30);
        Map<String, Long> counts = new HashMap<>();
        for (Subscriber s : subscriberRepository.findByStubFalse()) {
            if (s.getSignupDate() == null || s.getSignupDate().isBefore(cutoff)) {
                continue;
            }
            List<WebEvent> events = webEventRepository.findBySubscriberOrderByTimestampAsc(s);
            if (events.isEmpty()) {
                continue;
            }
            String page = Objects.toString(events.get(0).getPage(), "/");
            counts.merge(page, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> new PageCount(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public String exportCsv(String id) {
        SegmentResult result = runSegment(id, 5000);
        StringBuilder sb = new StringBuilder();
        sb.append("subscriber_ref,masked_email,status,acquisition_source,signup_date,last_open_date,engagement_score\n");
        for (SegmentMember m : result.members()) {
            sb.append(csv(m.subscriberRef())).append(',')
                    .append(csv(m.maskedEmail())).append(',')
                    .append(csv(m.status())).append(',')
                    .append(csv(m.acquisitionSource())).append(',')
                    .append(csv(m.signupDate() == null ? "" : m.signupDate().toString())).append(',')
                    .append(csv(m.lastOpenDate() == null ? "" : m.lastOpenDate().toString())).append(',')
                    .append(m.engagementScore()).append('\n');
        }
        return sb.toString();
    }

    private SegmentMember toMember(Subscriber s) {
        return new SegmentMember(
                piiService.subscriberRef(s.getEmail()),
                piiService.maskEmail(s.getEmail()),
                s.getStatus(),
                s.getAcquisitionSource(),
                s.getSignupDate(),
                s.getLastOpenDate(),
                engagementScore(s));
    }

    private int engagementScore(Subscriber s) {
        int score = 0;
        if (s.getLastOpenDate() != null) {
            score += 2;
        }
        List<AppEvent> events = appEventRepository.findBySubscriberOrderByTimestampAsc(s);
        for (AppEvent e : events) {
            if ("read_story".equals(e.getEventType())) {
                score += 3;
            } else if ("app_open".equals(e.getEventType())) {
                score += 1;
            }
        }
        score += webEventRepository.findBySubscriberOrderByTimestampAsc(s).size();
        return score;
    }

    private static boolean isCold(Subscriber s, LocalDate cutoff) {
        if (s.getLastOpenDate() == null) {
            return true;
        }
        return s.getLastOpenDate().isBefore(cutoff);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}
