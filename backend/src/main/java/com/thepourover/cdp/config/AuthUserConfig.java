package com.thepourover.cdp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Username/password users from {@code cdp.users} via Spring Security's UserDetailsService. */
@Configuration
public class AuthUserConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthUserConfig.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(CdpProperties properties, PasswordEncoder passwordEncoder) {
        // LinkedHashMap keeps declaration order; last entry wins on duplicate usernames.
        Map<String, UserDetails> unique = new LinkedHashMap<>();
        for (CdpProperties.DemoUser u : properties.users()) {
            if (u.username().isBlank()) {
                continue;
            }
            String key = u.username().trim().toLowerCase(Locale.ROOT);
            UserDetails details = User.builder()
                    .username(u.username().trim())
                    .password(passwordEncoder.encode(u.password()))
                    .roles("USER")
                    .build();
            if (unique.containsKey(key)) {
                log.warn("Duplicate cdp.users username '{}'; keeping last definition", u.username().trim());
            }
            unique.put(key, details);
        }
        if (unique.isEmpty()) {
            throw new IllegalStateException("cdp.users must define at least one username/password");
        }
        return new InMemoryUserDetailsManager(unique.values());
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
