package com.thepourover.cdp.config;

import com.thepourover.cdp.service.CsvLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final CsvLoaderService csvLoaderService;
    private final CdpProperties properties;

    public DataLoader(CsvLoaderService csvLoaderService, CdpProperties properties) {
        this.csvLoaderService = csvLoaderService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seedOnStartup()) {
            log.info("Startup seed skipped (cdp.seed-on-startup=false)");
            return;
        }
        var result = csvLoaderService.seed(false);
        log.info("Startup seed: {}", result);
    }
}
