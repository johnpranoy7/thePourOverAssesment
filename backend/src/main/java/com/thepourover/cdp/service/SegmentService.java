package com.thepourover.cdp.service;

import com.thepourover.cdp.config.CdpProperties;
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
import java.util.stream.Collectors;

@Service
public class SegmentService {

    public record SegmentDef(String id, String name, String description, Predicate<Subscriber> filter,
                             boolean sortByEngagement) {
    }

    /** Precomputed engagement points from a few aggregate queries (no per-subscriber lookups). */
    private record EngagementIndex(Map<Long, Integer> appPoints, Map<Long, Integer> webVisits) {
        int score(Subscriber s) {
            int score = s.getLastOpenDate() != null ? 2 : 0;
            Long id = s.getId();
            if (id != null) {
                score += appPoints.getOrDefault(id, 0);
                score += webVisits.getOrDefault(id, 0);
            }
            return score;
        }
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

    private List<SegmentDef> defs(EngagementIndex engagement, Set<Long> subscribersWithWebVisits) {
        LocalDate today = properties.today();
        LocalDate coldCutoff = today.minusDays(30);
        LocalDate newCutoff = today.minusDays(30);
        return List.of(
                new SegmentDef(
                        "instagram-cold-30d",
                        "Source & open activity",
                        "Default: Instagram with no open in 30 days (as of " + today
                                + "). Use the tab controls to change source, active vs inactive, and day window.",
                        s -> !s.isStub()
                                && "instagram".equalsIgnoreCase(nullToEmpty(s.getAcquisitionSource()))
                                && isCold(s, coldCutoff),
                        false),
                new SegmentDef(
                        "most-engaged",
                        "Most engaged readers",
                        "Newsletter profiles ranked by opens, story reads, app opens, and site visits.",
                        s -> !s.isStub() && engagement.score(s) > 0,
                        true),
                new SegmentDef(
                        "new-subscriber-first-pages",
                        "New subscribers",
                        "Signed up in the last 30 days (as of " + today
                                + ") with at least one linked web visit. Use the tab to change the day window.",
                        s -> !s.isStub()
                                && s.getSignupDate() != null
                                && !s.getSignupDate().isBefore(newCutoff)
                                && s.getId() != null
                                && subscribersWithWebVisits.contains(s.getId()),
                        false)
        );
    }

    @Transactional(readOnly = true)
    public List<SegmentMeta> listSegments() {
        EngagementIndex engagement = loadEngagementIndex();
        Set<Long> withVisits = subscribersWithWebVisits();
        List<Subscriber> all = subscriberRepository.findByStubFalse();
        List<SegmentMeta> out = new ArrayList<>();
        for (SegmentDef def : defs(engagement, withVisits)) {
            long count = all.stream().filter(def.filter()).count();
            out.add(new SegmentMeta(def.id(), def.name(), def.description(), count));
        }
        return out;
    }

    @Transactional(readOnly = true)
    public SegmentResult runSegment(String id, int limit) {
        return runSegment(id, limit, null);
    }

    /**
     * @param days optional window; for {@code new-subscriber-first-pages} overrides the default 30-day signup window.
     */
    @Transactional(readOnly = true)
    public SegmentResult runSegment(String id, int limit, Integer days) {
        if ("new-subscriber-first-pages".equals(id) && days != null) {
            return newSubscribers(days, limit);
        }

        EngagementIndex engagement = loadEngagementIndex();
        Set<Long> withVisits = subscribersWithWebVisits();
        SegmentDef def = defs(engagement, withVisits).stream()
                .filter(d -> d.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown segment: " + id));

        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(def.filter())
                .collect(Collectors.toCollection(ArrayList::new));
        if (def.sortByEngagement()) {
            matched.sort(Comparator.comparingInt(engagement::score).reversed());
        }
        List<SegmentMember> members = matched.stream()
                .limit(limit)
                .map(s -> toMember(s, engagement.score(s)))
                .toList();
        return new SegmentResult(def.id(), def.name(), def.description(), matched.size(), members);
    }

    @Transactional(readOnly = true)
    public SegmentResult newSubscribers(int days, int limit) {
        if (days < 0) {
            throw new IllegalArgumentException("days must be a whole number of 0 or more");
        }
        EngagementIndex engagement = loadEngagementIndex();
        Set<Long> withVisits = subscribersWithWebVisits();
        LocalDate today = properties.today();
        LocalDate cutoff = today.minusDays(days);
        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(s -> s.getSignupDate() != null
                        && !s.getSignupDate().isBefore(cutoff)
                        && s.getId() != null
                        && withVisits.contains(s.getId()))
                .collect(Collectors.toCollection(ArrayList::new));
        List<SegmentMember> members = matched.stream()
                .limit(limit)
                .map(s -> toMember(s, engagement.score(s)))
                .toList();
        return new SegmentResult(
                "new-subscriber-first-pages",
                "New subscribers",
                "Signed up in the last " + days + " day" + (days == 1 ? "" : "s")
                        + " (as of " + today + "; signup on/after " + cutoff
                        + ") with at least one linked web visit.",
                matched.size(),
                members);
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

    /**
     * @param activity {@code inactive} = no open for at least {@code days};
     *                 {@code active} = opened within the last {@code days}.
     */
    @Transactional(readOnly = true)
    public SegmentResult customSegment(String source, String activity, Integer days, int limit) {
        if (days != null && days < 0) {
            throw new IllegalArgumentException("days must be a whole number of 0 or more");
        }
        String mode = normalizeActivity(activity);
        int window = days == null ? ("active".equals(mode) ? 2 : 10) : days;

        EngagementIndex engagement = loadEngagementIndex();
        LocalDate today = properties.today();
        LocalDate boundary = today.minusDays(window);
        String src = source == null ? null : source.toLowerCase(Locale.ROOT);

        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(s -> src == null || src.equalsIgnoreCase(nullToEmpty(s.getAcquisitionSource())))
                .filter(s -> "active".equals(mode) ? isRecentlyActive(s, boundary) : isCold(s, boundary))
                .toList();
        List<SegmentMember> members = matched.stream()
                .limit(limit)
                .map(s -> toMember(s, engagement.score(s)))
                .toList();

        String sourceLabel = source == null || source.isBlank() ? "any source" : source;
        String activityLabel = "active".equals(mode)
                ? ("opened within the last " + window + " day" + (window == 1 ? "" : "s")
                        + " (as of " + today + "; last open on/after " + boundary + ")")
                : ("no open for at least " + window + " day" + (window == 1 ? "" : "s")
                        + " (as of " + today + "; last open before " + boundary + ")");
        return new SegmentResult(
                "custom",
                "Custom filter",
                sourceLabel + " · " + activityLabel,
                matched.size(),
                members);
    }

    /** Backward-compatible overload used by older callers (inactive / cold window). */
    @Transactional(readOnly = true)
    public SegmentResult customSegment(String source, Integer inactiveDays, int limit) {
        return customSegment(source, "inactive", inactiveDays, limit);
    }

    @Transactional(readOnly = true)
    public RedactedSegmentResult customSegmentRedacted(
            String source, String activity, Integer days, int limit) {
        SegmentResult result = customSegment(source, activity, days, limit);
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
            String key = row[0] == null || String.valueOf(row[0]).isBlank()
                    ? "unknown"
                    : String.valueOf(row[0]).toLowerCase(Locale.ROOT);
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
        return exportCsv(id, null);
    }

    @Transactional(readOnly = true)
    public String exportCsv(String id, Integer days) {
        return toCsv(runSegment(id, 5000, days));
    }

    @Transactional(readOnly = true)
    public String exportCustomCsv(String source, String activity, Integer days) {
        return toCsv(customSegment(source, activity, days, 5000));
    }

    private String toCsv(SegmentResult result) {
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

    private EngagementIndex loadEngagementIndex() {
        Map<Long, Integer> appPoints = new HashMap<>();
        for (Object[] row : appEventRepository.countGroupedBySubscriberAndEventType()) {
            Long subscriberId = (Long) row[0];
            String eventType = String.valueOf(row[1]);
            int count = ((Number) row[2]).intValue();
            int points = 0;
            if ("read_story".equals(eventType)) {
                points = count * 3;
            } else if ("app_open".equals(eventType)) {
                points = count;
            }
            if (subscriberId != null && points > 0) {
                appPoints.merge(subscriberId, points, Integer::sum);
            }
        }

        Map<Long, Integer> webVisits = new HashMap<>();
        for (Object[] row : webEventRepository.countGroupedBySubscriber()) {
            Long subscriberId = (Long) row[0];
            if (subscriberId != null) {
                webVisits.put(subscriberId, ((Number) row[1]).intValue());
            }
        }
        return new EngagementIndex(appPoints, webVisits);
    }

    private Set<Long> subscribersWithWebVisits() {
        return new HashSet<>(webEventRepository.findDistinctSubscriberIdsWithVisits());
    }

    private SegmentMember toMember(Subscriber s, int engagementScore) {
        return new SegmentMember(
                piiService.subscriberRef(s.getEmail()),
                piiService.maskEmail(s.getEmail()),
                s.getStatus(),
                s.getAcquisitionSource(),
                s.getSignupDate(),
                s.getLastOpenDate(),
                engagementScore);
    }

    private static boolean isCold(Subscriber s, LocalDate cutoff) {
        if (s.getLastOpenDate() == null) {
            return true;
        }
        return s.getLastOpenDate().isBefore(cutoff);
    }

    /** Opened on or after the window start (inclusive). Never-opened readers are excluded. */
    private static boolean isRecentlyActive(Subscriber s, LocalDate windowStart) {
        return s.getLastOpenDate() != null && !s.getLastOpenDate().isBefore(windowStart);
    }

    private static String normalizeActivity(String activity) {
        if (activity == null || activity.isBlank()) {
            return "inactive";
        }
        String mode = activity.trim().toLowerCase(Locale.ROOT);
        if ("active".equals(mode) || "recent".equals(mode)) {
            return "active";
        }
        if ("inactive".equals(mode) || "cold".equals(mode)) {
            return "inactive";
        }
        throw new IllegalArgumentException("activity must be active or inactive");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}
