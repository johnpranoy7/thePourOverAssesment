package com.thepourover.cdp.web;

import com.thepourover.cdp.service.CsvLoaderService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final CsvLoaderService csvLoaderService;

    public AdminController(CsvLoaderService csvLoaderService) {
        this.csvLoaderService = csvLoaderService;
    }

    @PostMapping("/reseed")
    public Map<String, Object> reseed() {
        return csvLoaderService.seed(true);
    }
}
