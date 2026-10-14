package com.thepourover.cdp.web;

import com.thepourover.cdp.service.SegmentService;
import com.thepourover.cdp.web.dto.Dtos.SegmentResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/segments")
public class SegmentController {

    private final SegmentService segmentService;

    public SegmentController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GetMapping
    public Object listOrCustom(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer inactive_days) {
        if (source != null || inactive_days != null) {
            return segmentService.customSegment(source, inactive_days, 100);
        }
        return Map.of("segments", segmentService.listSegments());
    }

    @GetMapping("/{id}")
    public SegmentResult run(@PathVariable String id) {
        return segmentService.runSegment(id, 100);
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(@PathVariable String id) {
        String csv = segmentService.exportCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
