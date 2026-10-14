package com.thepourover.cdp.web;

import com.thepourover.cdp.service.WebhookIngestService;
import com.thepourover.cdp.web.dto.Dtos.WebhookPayload;
import com.thepourover.cdp.web.dto.Dtos.WebhookResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebhookController {

    private final WebhookIngestService webhookIngestService;

    public WebhookController(WebhookIngestService webhookIngestService) {
        this.webhookIngestService = webhookIngestService;
    }

    @PostMapping("/webhooks/app")
    public WebhookResponse ingest(@RequestBody WebhookPayload payload) {
        return webhookIngestService.ingest(payload);
    }
}
