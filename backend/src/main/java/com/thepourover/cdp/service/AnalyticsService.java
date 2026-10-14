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
import java.util.stream.Collectors;

/**
 * Growth analytics queries. Three first-class cuts (open activity, most engaged, new subscribers)
 * plus snapshot helpers. CSV export always goes through {@link #toCsv(AnalyticsResult)}.
 */
@Service
public class AnalyticsService {

    public static final String OPEN_ACTIVITY = "open-activity";
    public static final String MOST_ENGAGED = "most-engaged";
    public static final String NEW_SUBSCRIBERS = "new-subscribers";

    private static final int DEFAULT_EXPORT_LIMIT = 5000;

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

    public AnalyticsService(
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

    // ---- Catalog (Analytics tabs) ----

    @Transactional(readOnly = true)
    public List<AnalyticsMeta> listTabs() {
        EngagementIndex engagement = loadEngagementIndex();
        Set<Long> withVisits = subscribersWithWebVisits();
        LocalDate today = properties.today();

        long openActivityDefault = openActivity("instagram", "inactive", 30, 0).count();
        long mostEngaged = subscriberRepository.findByStubFalse().stream()
                .filter(s -> engagement.score(s) > 0)
                .count();
        LocalDate newCutoff = today.minusDays(30);
        long newSubs = subscriberRepository.findByStubFalse().stream()
                .filter(s -> s.getSignupDate() != null
                        && !s.getSignupDate().isBefore(newCutoff)
                        && s.getId() != null
                        && withVisits.contains(s.getId()))
                .count();

        return List.of(
                new AnalyticsMeta(
                        OPEN_ACTIVITY,
                        "Source & open activity",
                        "Filter by acquisition source and active vs inactive opens (default: Instagram cold 30d as of "
                                + today + ").",
                        openActivityDefault),
                new AnalyticsMeta(
                        MOST_ENGAGED,
                        "Most engaged readers",
                        "Newsletter profiles ranked by opens, story reads, app opens, and site visits.",
                        mostEngaged),
                new AnalyticsMeta(
                        NEW_SUBSCRIBERS,
                        "New subscribers",
                        "Signed up in the last N days (default 30 as of " + today
                                + ") with at least one linked web visit.",
                        newSubs));
    }

    // ---- Queries ----

    @Transactional(readOnly = true)
    public AnalyticsResult openActivity(String source, String activity, Integer days, int limit) {
        requireNonNegativeDays(days);
        String mode = normalizeActivity(activity);
        int window = days == null ? ("active".equals(mode) ? 2 : 30) : days;

        EngagementIndex engagement = loadEngagementIndex();
        LocalDate today = properties.today();
        LocalDate boundary = today.minusDays(window);
        String src = blankToNull(source);

        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(s -> src == null || src.equalsIgnoreCase(nullToEmpty(s.getAcquisitionSource())))
                .filter(s -> "active".equals(mode) ? isRecentlyActive(s, boundary) : isCold(s, boundary))
                .collect(Collectors.toCollection(ArrayList::new));

        return buildResult(
                OPEN_ACTIVITY,
                "Source & open activity",
                describeOpenActivity(src, mode, window, today, boundary),
                matched,
                engagement,
                limit,
                false);
    }

    @Transactional(readOnly = true)
    public AnalyticsResult mostEngaged(int limit) {
        EngagementIndex engagement = loadEngagementIndex();
        List<Subscriber> matched = subscriberRepository.findByStubFalse().stream()
                .filter(s -> engagement.score(s) > 0)
                .collect(Collectors.toCollection(ArrayList::new));
        return buildResult(
                MOST_ENGAGED,
                "Most engaged readers",
                "Newsletter profiles ranked by opens, story reads, app opens, and site visits.",
                matched,
                engagement,
                limit,
                true);
    }

    @Transactional(readOnly = true)
    public AnalyticsResult newSubscribers(int days, int limit) {
        requireNonNegativeDays(days);
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

        return buildResult(
                NEW_SUBSCRIBERS,
                "New subscribers",
                "Signed up in the last " + days + dayWord(days)
                        + " (as of " + today + "; signup on/after " + cutoff
                        + ") with at least one linked web visit.",
                matched,
                engagement,
                limit,
                false);
    }

    // ---- Redacted (AI tools) ----

    @Transactional(readOnly = true)
    public RedactedAnalyticsResult openActivityRedacted(
            String source, String activity, Integer days, int limit) {
        return toRedacted(openActivity(source, activity, days, limit));
    }

    @Transactional(readOnly = true)
    public RedactedAnalyticsResult mostEngagedRedacted(int limit) {
        return toRedacted(mostEngaged(limit));
    }

    @Transactional(readOnly = true)
    public RedactedAnalyticsResult newSubscribersRedacted(int days, int limit) {
        return toRedacted(newSubscribers(days, limit));
    }

    // ---- Export ----

    @Transactional(readOnly = true)
    public String exportOpenActivityCsv(String source, String activity, Integer days) {
        return toCsv(openActivity(source, activity, days, DEFAULT_EXPORT_LIMIT));
    }

    @Transactional(readOnly = true)
    public String exportMostEngagedCsv() {
        return toCsv(mostEngaged(DEFAULT_EXPORT_LIMIT));
    }

    @Transactional(readOnly = true)
    public String exportNewSubscribersCsv(int days) {
        return toCsv(newSubscribers(days, DEFAULT_EXPORT_LIMIT));
    }

    // ---- Snapshot / pages ----

    @Transactional(readOnly = true)
    public SnapshotResponse snapshot() {
        Map<String, Long> mix = new LinkedHashMap<>();
        for (Object[] row : subscriberRepository.countByAcquisitionSource()) {
            String key = row[0] == null || String.valueOf(row[0]).isBlank()
                    ? "unknown"
                    : String.valueOf(row[0]).toLowerCase(Locale.ROOT);
            mix.put(key, ((Number) row[1]).longValue());
        }
        long coldInstagram30d = openActivity("instagram", "inactive", 30, 0).count();
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
                coldInstagram30d,
                recent.size());
        return new SnapshotResponse(snap, recent);
    }

    @Transactional(readOnly = true)
    public List<PageCount> topPagesForNewSubscribers(int signupDays, int limit) {
        requireNonNegativeDays(signupDays);
        LocalDate cutoff = properties.today().minusDays(signupDays);
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

    // ---- Internals ----

    private AnalyticsResult buildResult(
            String id,
            String name,
            String description,
            List<Subscriber> matched,
            EngagementIndex engagement,
            int limit,
            boolean sortByEngagement) {
        if (sortByEngagement) {
            matched.sort(Comparator.comparingInt(engagement::score).reversed());
        }
        List<AnalyticsMember> members = matched.stream()
                .limit(Math.max(0, limit))
                .map(s -> toMember(s, engagement.score(s)))
                .toList();
        return new AnalyticsResult(id, name, description, matched.size(), members);
    }

    private RedactedAnalyticsResult toRedacted(AnalyticsResult result) {
        List<RedactedAnalyticsMember> members = result.members().stream()
                .map(m -> new RedactedAnalyticsMember(
                        m.subscriberRef(),
                        m.status(),
                        m.acquisitionSource(),
                        m.signupDate(),
                        m.lastOpenDate(),
                        m.engagementScore()))
                .toList();
        RedactedAnalyticsResult redacted = new RedactedAnalyticsResult(
                result.id(), result.name(), result.description(), result.count(), members);
        piiService.assertNoEmails(redacted, "analytics");
        return redacted;
    }

    private String toCsv(AnalyticsResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("subscriber_ref,masked_email,status,acquisition_source,signup_date,last_open_date,engagement_score\n");
        for (AnalyticsMember m : result.members()) {
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

    private AnalyticsMember toMember(Subscriber s, int engagementScore) {
        return new AnalyticsMember(
                piiService.subscriberRef(s.getEmail()),
                piiService.maskEmail(s.getEmail()),
                s.getStatus(),
                s.getAcquisitionSource(),
                s.getSignupDate(),
                s.getLastOpenDate(),
                engagementScore);
    }

    private static String describeOpenActivity(
            String source, String mode, int window, LocalDate today, LocalDate boundary) {
        String sourceLabel = source == null ? "any source" : source;
        if ("active".equals(mode)) {
            return sourceLabel + " · opened within the last " + window + dayWord(window)
                    + " (as of " + today + "; last open on/after " + boundary + ")";
        }
        return sourceLabel + " · no open for at least " + window + dayWord(window)
                + " (as of " + today + "; last open before " + boundary + ")";
    }

    private static void requireNonNegativeDays(Integer days) {
        if (days != null && days < 0) {
            throw new IllegalArgumentException("days must be a whole number of 0 or more");
        }
    }

    private static boolean isCold(Subscriber s, LocalDate cutoff) {
        return s.getLastOpenDate() == null || s.getLastOpenDate().isBefore(cutoff);
    }

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

    private static String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim().toLowerCase(Locale.ROOT);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String dayWord(int days) {
        return days == 1 ? " day" : " days";
    }

    private static String csv(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }
}
