package com.thepourover.cdp.web;

import com.thepourover.cdp.service.AnalyticsService;
import com.thepourover.cdp.web.dto.Dtos.AnalyticsResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private static final int UI_LIMIT = 100;

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public Map<String, Object> list() {
        return Map.of("tabs", analyticsService.listTabs());
    }

    @GetMapping("/open-activity")
    public AnalyticsResult openActivity(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String activity,
            @RequestParam(required = false) Integer days) {
        return run(() -> analyticsService.openActivity(source, activity, days, UI_LIMIT));
    }

    @GetMapping("/open-activity/export")
    public ResponseEntity<String> exportOpenActivity(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String activity,
            @RequestParam(required = false) Integer days) {
        return csv("open-activity.csv",
                () -> analyticsService.exportOpenActivityCsv(source, activity, days));
    }

    @GetMapping("/most-engaged")
    public AnalyticsResult mostEngaged() {
        return run(() -> analyticsService.mostEngaged(UI_LIMIT));
    }

    @GetMapping("/most-engaged/export")
    public ResponseEntity<String> exportMostEngaged() {
        return csv("most-engaged.csv", analyticsService::exportMostEngagedCsv);
    }

    @GetMapping("/new-subscribers")
    public AnalyticsResult newSubscribers(@RequestParam(defaultValue = "30") Integer days) {
        return run(() -> analyticsService.newSubscribers(days, UI_LIMIT));
    }

    @GetMapping("/new-subscribers/export")
    public ResponseEntity<String> exportNewSubscribers(@RequestParam(defaultValue = "30") Integer days) {
        return csv("new-subscribers.csv", () -> analyticsService.exportNewSubscribersCsv(days));
    }

    private AnalyticsResult run(AnalyticsCall call) {
        try {
            return call.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private ResponseEntity<String> csv(String filename, AnalyticsCallString call) {
        try {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(call.get());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @FunctionalInterface
    private interface AnalyticsCall {
        AnalyticsResult get();
    }

    @FunctionalInterface
    private interface AnalyticsCallString {
        String get();
    }
}
