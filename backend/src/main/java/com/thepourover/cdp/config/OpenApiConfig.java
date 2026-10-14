package com.thepourover.cdp.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    public static final String WEBHOOK_BEARER = "WebhookBearer";
    public static final String UI_SESSION = "UiSessionCookie";

    @Bean
    OpenAPI cdpOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("The Pour Over — Mini CDP API")
                        .description("""
                                Backend for the Growth mini CDP.

                                **Add live app events:** use `POST /webhooks/app` (Authorize with \
                                WebhookBearer = your `WEBHOOK_SECRET`, default `dev-webhook-secret`).

                                **UI session APIs** (`/api/**`): log in via `POST /api/auth/login` first \
                                (cookie session), then call lookup / analytics / snapshot / chat / reseed.
                                """)
                        .version("0.0.1")
                        .contact(new Contact().name("The Pour Over CDP")))
                .servers(List.of(new Server().url("/").description("This host")))
                .components(new Components()
                        .addSecuritySchemes(WEBHOOK_BEARER, new SecurityScheme()
                                .name(WEBHOOK_BEARER)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("WEBHOOK_SECRET")
                                .description("Value of WEBHOOK_SECRET only (Swagger adds the Bearer prefix). Default: dev-webhook-secret"))
                        .addSecuritySchemes(UI_SESSION, new SecurityScheme()
                                .name("JSESSIONID")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .description("Set automatically after POST /api/auth/login with UI password")));
    }
}
