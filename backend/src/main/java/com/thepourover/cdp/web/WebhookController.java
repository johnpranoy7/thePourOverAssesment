package com.thepourover.cdp.web;

import com.thepourover.cdp.config.OpenApiConfig;
import com.thepourover.cdp.service.WebhookIngestService;
import com.thepourover.cdp.web.dto.Dtos.WebhookPayload;
import com.thepourover.cdp.web.dto.Dtos.WebhookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Webhooks", description = "Live mobile app event ingest — use this from Swagger to add demo data")
public class WebhookController {

    private final WebhookIngestService webhookIngestService;

    public WebhookController(WebhookIngestService webhookIngestService) {
        this.webhookIngestService = webhookIngestService;
    }

    @PostMapping("/webhooks/app")
    @SecurityRequirement(name = OpenApiConfig.WEBHOOK_BEARER)
    @Operation(
            summary = "Ingest an app event",
            description = """
                    Accepts live app events (`app_open`, `read_story`, `link_click`, `login`).
                    Dedupes by `event_id`, supports anonymous device events (`user_id` null),
                    and merges onto the subscriber after login. Unknown `user_id` creates a stub profile.
                    """)
    public WebhookResponse ingest(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = {
                                    @ExampleObject(
                                            name = "Anonymous app_open",
                                            value = """
                                                    {
                                                      "event_id": "evt_demo_anon_001",
                                                      "event": "app_open",
                                                      "user_id": null,
                                                      "device_id": "d_demo_device",
                                                      "timestamp": "2026-09-28T14:22:05Z",
                                                      "properties": {}
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "Login then attach device",
                                            value = """
                                                    {
                                                      "event_id": "evt_demo_login_001",
                                                      "event": "login",
                                                      "user_id": "u_2b6447a3",
                                                      "device_id": "d_demo_device",
                                                      "timestamp": "2026-09-28T14:23:05Z",
                                                      "properties": {}
                                                    }
                                                    """),
                                    @ExampleObject(
                                            name = "Read story (known user)",
                                            value = """
                                                    {
                                                      "event_id": "evt_demo_read_001",
                                                      "event": "read_story",
                                                      "user_id": "u_2b6447a3",
                                                      "device_id": "d_demo_device",
                                                      "timestamp": "2026-09-28T14:24:05Z",
                                                      "properties": { "story": "supreme-court-ruling-explained" }
                                                    }
                                                    """)
                            }))
            @RequestBody WebhookPayload payload) {
        return webhookIngestService.ingest(payload);
    }
}
