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

    @Tool(description = "Count/filter subscribers by acquisition source and inactivity days relative to 2026-09-28. Returns hashed refs only.")
    public RedactedSegmentResult countCustomSegment(
            @ToolParam(description = "Acquisition source e.g. instagram", required = false) String source,
            @ToolParam(description = "Days without open", required = false) Integer inactiveDays,
            @ToolParam(description = "Max members", required = false) Integer limit) {
        RedactedSegmentResult result = segmentService.customSegmentRedacted(
                source, inactiveDays, limit == null ? 25 : limit);
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
