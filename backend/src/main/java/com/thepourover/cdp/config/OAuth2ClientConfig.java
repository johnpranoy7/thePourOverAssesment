package com.thepourover.cdp.config;

import com.thepourover.cdp.security.EmptyClientRegistrationRepository;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientProperties;
import org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientPropertiesMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Map;

/**
 * Optional GitHub OAuth via standard {@code spring.security.oauth2.client} properties.
 * Blank client-id/secret → GitHub button stays hidden.
 */
@Configuration
@EnableConfigurationProperties(OAuth2ClientProperties.class)
public class OAuth2ClientConfig {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientProperties properties) {
        OAuth2ClientProperties enabled = new OAuth2ClientProperties();
        properties.getRegistration().forEach((id, registration) -> {
            if (StringUtils.hasText(registration.getClientId())
                    && StringUtils.hasText(registration.getClientSecret())) {
                enabled.getRegistration().put(id, registration);
            }
        });
        enabled.getProvider().putAll(properties.getProvider());
        if (enabled.getRegistration().isEmpty()) {
            return new EmptyClientRegistrationRepository();
        }
        Map<String, ClientRegistration> mapped =
                new OAuth2ClientPropertiesMapper(enabled).asClientRegistrations();
        return new InMemoryClientRegistrationRepository(new ArrayList<>(mapped.values()));
    }

    public static boolean hasRegistrations(ClientRegistrationRepository repository) {
        return repository instanceof Iterable<?> iterable && iterable.iterator().hasNext();
    }
}
