package com.thepourover.cdp.web;

import com.thepourover.cdp.service.AnalyticsService;
import com.thepourover.cdp.web.dto.Dtos.SnapshotResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshot")
public class SnapshotController {

    private final AnalyticsService analyticsService;

    public SnapshotController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public SnapshotResponse snapshot() {
        return analyticsService.snapshot();
    }
}
