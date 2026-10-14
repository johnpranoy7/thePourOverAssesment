package com.thepourover.cdp.ai;

import com.thepourover.cdp.service.PiiService;
import com.thepourover.cdp.service.SegmentService;
import com.thepourover.cdp.web.dto.Dtos.GrowthSnapshot;
import com.thepourover.cdp.web.dto.Dtos.PageCount;
import com.thepourover.cdp.web.dto.Dtos.RedactedSegmentResult;
import com.thepourover.cdp.web.dto.Dtos.SegmentMeta;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring AI tools. Return redacted DTOs only — never raw emails.
 */
@Component
public class CdpTools {

    private final SegmentService segmentService;
    private final PiiService piiService;

    public CdpTools(SegmentService segmentService, PiiService piiService) {
        this.segmentService = segmentService;
        this.piiService = piiService;
    }

    @Tool(description = "List available Growth segments with counts. No PII.")
    public List<SegmentMeta> listSegments() {
        List<SegmentMeta> result = segmentService.listSegments();
        piiService.assertNoEmails(result, "listSegments");
        return result;
    }

    @Tool(description = "Run a preset segment by id (instagram-cold-30d, most-engaged, new-subscriber-first-pages). Returns hashed subscriber_ref only.")
    public RedactedSegmentResult runSegment(
            @ToolParam(description = "Segment id") String segmentId,
            @ToolParam(description = "Max members", required = false) Integer limit) {
        RedactedSegmentResult result = segmentService.runSegmentRedacted(
                segmentId, limit == null ? 25 : limit);
        piiService.assertNoEmails(result, "runSegment");
        return result;
    }

    @Tool(description = "Filter subscribers by acquisition source and open activity as of 2026-09-28. activity=inactive (cold for at least N days) or active (opened within last N days). Returns total count plus a small member sample for illustration.")
    public RedactedSegmentResult countCustomSegment(
            @ToolParam(description = "Acquisition source e.g. instagram", required = false) String source,
            @ToolParam(description = "inactive (default) or active", required = false) String activity,
            @ToolParam(description = "Exact day window as an integer from the user. Do not invent a value for vague phrases. For inactive = min days without open; for active = opened within last N days.", required = false) Integer days,
            @ToolParam(description = "Max sample members to return (not the total count). Default a small sample.", required = false) Integer limit) {
        int sampleLimit = limit == null ? 10 : Math.max(0, limit);
        RedactedSegmentResult result = segmentService.customSegmentRedacted(
                source, activity, days, sampleLimit);
        piiService.assertNoEmails(result, "countCustomSegment");
        return result;
    }

    @Tool(description = "High-level Growth snapshot metrics for the CDP.")
    public GrowthSnapshot growthSnapshot() {
        GrowthSnapshot snap = segmentService.snapshot().snapshot();
        piiService.assertNoEmails(snap, "growthSnapshot");
        return snap;
    }

    @Tool(description = "Which pages new subscribers (last 30 days) hit first.")
    public List<PageCount> topPagesForNewSubscribers(
            @ToolParam(description = "Max pages", required = false) Integer limit) {
        List<PageCount> result = segmentService.topPagesForNewSubscribers(limit == null ? 10 : limit);
        piiService.assertNoEmails(result, "topPagesForNewSubscribers");
        return result;
    }
}
