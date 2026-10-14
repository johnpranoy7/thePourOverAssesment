package com.thepourover.cdp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CdpApplication {

    public static void main(String[] args) {
        SpringApplication.run(CdpApplication.class, args);
    }
}
