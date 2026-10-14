package com.thepourover.cdp.web;

import com.thepourover.cdp.service.SegmentService;
import com.thepourover.cdp.web.dto.Dtos.SegmentResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
            @RequestParam(required = false) String activity,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer inactive_days) {
        Integer window = days != null ? days : inactive_days;
        if (window != null && window < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "days must be a whole number of 0 or more");
        }
        boolean custom = source != null || activity != null || window != null;
        if (custom) {
            try {
                return segmentService.customSegment(source, activity, window, 100);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
        }
        return Map.of("segments", segmentService.listSegments());
    }

    @GetMapping("/custom/export")
    public ResponseEntity<String> exportCustom(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String activity,
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer inactive_days) {
        Integer window = days != null ? days : inactive_days;
        if (window != null && window < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "days must be a whole number of 0 or more");
        }
        try {
            String csv = segmentService.exportCustomCsv(source, activity, window);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"custom-filter.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    public SegmentResult run(
            @PathVariable String id,
            @RequestParam(required = false) Integer days) {
        if (days != null && days < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "days must be a whole number of 0 or more");
        }
        try {
            return segmentService.runSegment(id, 100, days);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<String> export(
            @PathVariable String id,
            @RequestParam(required = false) Integer days) {
        if (days != null && days < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "days must be a whole number of 0 or more");
        }
        try {
            String csv = segmentService.exportCsv(id, days);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }
}
