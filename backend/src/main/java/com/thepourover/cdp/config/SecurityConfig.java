package com.thepourover.cdp.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thepourover.cdp.security.CsrfCookieFilter;
import com.thepourover.cdp.security.GithubAllowlistOAuth2UserService;
import com.thepourover.cdp.security.SpaCsrfTokenRequestHandler;
import com.thepourover.cdp.security.WebhookSecretFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${spring.h2.console.enabled:false}")
    private boolean h2ConsoleEnabled;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            WebhookSecretFilter webhookSecretFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            GithubAllowlistOAuth2UserService githubAllowlistOAuth2UserService,
            CdpProperties cdpProperties) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        .ignoringRequestMatchers(new AntPathRequestMatcher("/webhooks/app", "POST")))
                .cors(Customizer.withDefaults())
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/api/auth/login", "/api/auth/me", "/api/auth/config").permitAll()
                            .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                            .requestMatchers(HttpMethod.POST, "/webhooks/app").permitAll()
                            .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                    "/login", "/lookup", "/segments", "/analytics", "/assistant").permitAll();
                    if (h2ConsoleEnabled) {
                        auth.requestMatchers("/h2-console", "/h2-console/**").authenticated();
                    } else {
                        auth.requestMatchers("/h2-console", "/h2-console/**").denyAll();
                    }
                    auth.requestMatchers(
                                    "/swagger-ui.html",
                                    "/swagger-ui/**",
                                    "/v3/api-docs",
                                    "/v3/api-docs/**").authenticated()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            res.setContentType("application/json");
                            new ObjectMapper().writeValue(res.getOutputStream(),
                                    Map.of("error", "Unauthorized"));
                        }))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler((req, res, auth) -> {
                            res.setStatus(HttpServletResponse.SC_OK);
                            res.setContentType("application/json");
                            new ObjectMapper().writeValue(res.getOutputStream(), Map.of("ok", true));
                        }))
                .addFilterBefore(webhookSecretFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);

        if (OAuth2ClientConfig.hasRegistrations(clientRegistrationRepository)
                && cdpProperties.githubLoginEnabled()) {
            OAuth2UserService<OAuth2UserRequest, OAuth2User> userService = githubAllowlistOAuth2UserService;
            String origin = cdpProperties.uiOrigin();
            String successUrl = origin.isBlank() ? "/" : origin + "/";
            String failureUrl = origin.isBlank() ? "/login?error=oauth" : origin + "/login?error=oauth";
            http.oauth2Login(oauth -> oauth
                    .userInfoEndpoint(userInfo -> userInfo.userService(userService))
                    .defaultSuccessUrl(successUrl, true)
                    .failureUrl(failureUrl));
        }

        return http.build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String extra = System.getenv().getOrDefault("CORS_ALLOWED_ORIGINS", "");
        List<String> origins = new java.util.ArrayList<>(List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:8080",
                "https://pourover.johnpranoy7.dev"));
        if (!extra.isBlank()) {
            Arrays.stream(extra.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(origins::add);
        }
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of("X-CSRF-TOKEN"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
