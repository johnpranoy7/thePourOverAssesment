package com.thepourover.cdp.web;

import com.thepourover.cdp.config.CdpProperties;
import com.thepourover.cdp.config.OAuth2ClientConfig;
import com.thepourover.cdp.security.LoginRateLimiter;
import com.thepourover.cdp.web.dto.Dtos.AuthConfigResponse;
import com.thepourover.cdp.web.dto.Dtos.AuthResponse;
import com.thepourover.cdp.web.dto.Dtos.LoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final ClientRegistrationRepository clientRegistrationRepository;
    private final CdpProperties cdpProperties;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            ClientRegistrationRepository clientRegistrationRepository,
            CdpProperties cdpProperties,
            LoginRateLimiter loginRateLimiter) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.clientRegistrationRepository = clientRegistrationRepository;
        this.cdpProperties = cdpProperties;
        this.loginRateLimiter = loginRateLimiter;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        boolean github = OAuth2ClientConfig.hasRegistrations(clientRegistrationRepository)
                && clientRegistrationRepository.findByRegistrationId("github") != null
                && cdpProperties.githubLoginEnabled();
        var demo = cdpProperties.users().stream()
                .filter(u -> StringUtils.hasText(u.username()))
                .findFirst()
                .orElse(null);
        return new AuthConfigResponse(
                github,
                github ? "/oauth2/authorization/github" : null,
                demo == null ? null : demo.username(),
                demo == null ? null : demo.password());
    }

    @PostMapping("/login")
    public Map<String, Object> login(
            @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (body == null || !StringUtils.hasText(body.username()) || body.password() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and password required");
        }
        String clientKey = clientKey(request);
        loginRateLimiter.checkAllowed(clientKey);
        try {
            Authentication auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(body.username().trim(), body.password()));

            // Session fixation: drop any pre-login session before issuing a new one.
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                existing.invalidate();
            }

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            HttpSession session = request.getSession(true);
            securityContextRepository.saveContext(context, request, response);
            session.setAttribute("cdpAuthenticated", true);
            loginRateLimiter.recordSuccess(clientKey);
            return Map.of("ok", true);
        } catch (AuthenticationException e) {
            loginRateLimiter.recordFailure(clientKey);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
    }

    @GetMapping("/me")
    public AuthResponse me(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
            return new AuthResponse(false, null);
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails user) {
            return new AuthResponse(true, user.getUsername());
        }
        if (principal instanceof OAuth2User oauth2) {
            String login = oauth2.getAttribute("login");
            if (!StringUtils.hasText(login)) {
                login = oauth2.getAttribute("name");
            }
            if (!StringUtils.hasText(login)) {
                login = oauth2.getName();
            }
            return new AuthResponse(true, login);
        }
        return new AuthResponse(true, String.valueOf(principal));
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
