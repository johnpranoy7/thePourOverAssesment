package com.thepourover.cdp.ai;

import com.thepourover.cdp.service.AnalyticsService;
import com.thepourover.cdp.service.PiiService;
import com.thepourover.cdp.web.dto.Dtos.AnalyticsMeta;
import com.thepourover.cdp.web.dto.Dtos.GrowthSnapshot;
import com.thepourover.cdp.web.dto.Dtos.PageCount;
import com.thepourover.cdp.web.dto.Dtos.RedactedAnalyticsResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Spring AI tools over {@link AnalyticsService}. One tool per Analytics query. */
@Component
public class CdpTools {

    private final AnalyticsService analyticsService;
    private final PiiService piiService;

    public CdpTools(AnalyticsService analyticsService, PiiService piiService) {
        this.analyticsService = analyticsService;
        this.piiService = piiService;
    }

    @Tool(description = "List Analytics tabs with default counts. No PII.")
    public List<AnalyticsMeta> listAnalyticsTabs() {
        List<AnalyticsMeta> result = analyticsService.listTabs();
        piiService.assertNoEmails(result, "listAnalyticsTabs");
        return result;
    }

    @Tool(description = "Source & open activity (Analytics tab). Filter by source, active|inactive, and day window as of 2026-09-28.")
    public RedactedAnalyticsResult countOpenActivity(
            @ToolParam(description = "Acquisition source e.g. instagram", required = false) String source,
            @ToolParam(description = "inactive (default) or active", required = false) String activity,
            @ToolParam(description = "Exact day window from the user", required = false) Integer days,
            @ToolParam(description = "Max sample members", required = false) Integer limit) {
        int sample = limit == null ? 10 : Math.max(0, limit);
        RedactedAnalyticsResult result =
                analyticsService.openActivityRedacted(source, activity, days, sample);
        piiService.assertNoEmails(result, "countOpenActivity");
        return result;
    }

    @Tool(description = "Most engaged readers by engagement score (Analytics tab).")
    public RedactedAnalyticsResult countMostEngaged(
            @ToolParam(description = "Max sample members", required = false) Integer limit) {
        int sample = limit == null ? 25 : Math.max(0, limit);
        RedactedAnalyticsResult result = analyticsService.mostEngagedRedacted(sample);
        piiService.assertNoEmails(result, "countMostEngaged");
        return result;
    }

    @Tool(description = "New subscribers: signed up within N days AND has a linked web visit. Always use for new-subscriber counts.")
    public RedactedAnalyticsResult countNewSubscribers(
            @ToolParam(description = "Exact signup window in days") Integer days,
            @ToolParam(description = "Max sample members", required = false) Integer limit) {
        if (days == null) {
            throw new IllegalArgumentException("days is required for countNewSubscribers");
        }
        int sample = limit == null ? 10 : Math.max(0, limit);
        RedactedAnalyticsResult result = analyticsService.newSubscribersRedacted(days, sample);
        piiService.assertNoEmails(result, "countNewSubscribers");
        return result;
    }

    @Tool(description = "Growth snapshot metrics.")
    public GrowthSnapshot growthSnapshot() {
        GrowthSnapshot snap = analyticsService.snapshot().snapshot();
        piiService.assertNoEmails(snap, "growthSnapshot");
        return snap;
    }

    @Tool(description = "First pages hit by new subscribers (signup window + web visit). Default days=30.")
    public List<PageCount> topPagesForNewSubscribers(
            @ToolParam(description = "Signup window in days", required = false) Integer days,
            @ToolParam(description = "Max pages", required = false) Integer limit) {
        List<PageCount> result = analyticsService.topPagesForNewSubscribers(
                days == null ? 30 : days,
                limit == null ? 10 : limit);
        piiService.assertNoEmails(result, "topPagesForNewSubscribers");
        return result;
    }
}
