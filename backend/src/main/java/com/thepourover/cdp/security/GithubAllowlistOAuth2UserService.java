package com.thepourover.cdp.security;

import com.thepourover.cdp.config.CdpProperties;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/** Rejects GitHub users who are not on {@code cdp.github-allowed-logins}. */
@Component
public class GithubAllowlistOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final CdpProperties properties;
    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();

    public GithubAllowlistOAuth2UserService(CdpProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(userRequest);
        Set<String> allow = properties.githubAllowlist();
        if (allow.isEmpty()) {
            throw deny("GitHub sign-in is disabled until GITHUB_ALLOWED_LOGINS is set");
        }
        String login = attr(user, "login");
        String email = attr(user, "email");
        if ((login != null && allow.contains(login)) || (email != null && allow.contains(email))) {
            return user;
        }
        throw deny("GitHub account is not on the allowlist");
    }

    private static String attr(OAuth2User user, String name) {
        Object value = user.getAttribute(name);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return null;
        }
        return String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    private static OAuth2AuthenticationException deny(String message) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", message, null), message);
    }
}
