package com.thepourover.cdp.web;

import com.thepourover.cdp.service.SegmentService;
import com.thepourover.cdp.web.dto.Dtos.SnapshotResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/snapshot")
public class SnapshotController {

    private final SegmentService segmentService;

    public SnapshotController(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    @GetMapping
    public SnapshotResponse snapshot() {
        return segmentService.snapshot();
    }
}
