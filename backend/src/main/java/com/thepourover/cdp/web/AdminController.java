package com.thepourover.cdp.web;

import com.thepourover.cdp.config.OpenApiConfig;
import com.thepourover.cdp.service.CsvLoaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Reload CSV seed data (requires UI login session)")
@SecurityRequirement(name = OpenApiConfig.UI_SESSION)
public class AdminController {

    private final CsvLoaderService csvLoaderService;

    public AdminController(CsvLoaderService csvLoaderService) {
        this.csvLoaderService = csvLoaderService;
    }

    @PostMapping("/reseed")
    @Operation(summary = "Reseed from CSVs", description = "Clears and reloads subscribers, app_users, and web_events from classpath CSVs.")
    public Map<String, Object> reseed() {
        return csvLoaderService.seed(true);
    }
}
