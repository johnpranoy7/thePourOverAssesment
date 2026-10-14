package com.thepourover.cdp.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.thepourover.cdp.config.CdpProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

class GithubAllowlistOAuth2UserServiceTest {

    @Test
    void rejectsWhenAllowlistEmpty() {
        GithubAllowlistOAuth2UserService service = serviceWithAllowlist("");
        stubDelegate(service, user("alice", "a@example.com"));
        assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(mock(OAuth2UserRequest.class)));
    }

    @Test
    void rejectsUserNotOnAllowlist() {
        GithubAllowlistOAuth2UserService service = serviceWithAllowlist("bob,carol@example.com");
        stubDelegate(service, user("alice", "a@example.com"));
        assertThrows(OAuth2AuthenticationException.class, () -> service.loadUser(mock(OAuth2UserRequest.class)));
    }

    @Test
    void allowsMatchingLogin() {
        GithubAllowlistOAuth2UserService service = serviceWithAllowlist("Alice,carol@example.com");
        OAuth2User alice = user("alice", "a@example.com");
        stubDelegate(service, alice);
        OAuth2User loaded = service.loadUser(mock(OAuth2UserRequest.class));
        org.junit.jupiter.api.Assertions.assertEquals("alice", loaded.getAttribute("login"));
    }

    private static GithubAllowlistOAuth2UserService serviceWithAllowlist(String allow) {
        CdpProperties props = new CdpProperties(
                "secret", "salt", LocalDate.of(2026, 9, 28), false, List.of(), allow, "");
        return new GithubAllowlistOAuth2UserService(props);
    }

    private static OAuth2User user(String login, String email) {
        return new DefaultOAuth2User(
                List.of(),
                Map.of("login", login, "email", email, "id", "1", "name", login),
                "login");
    }

    private static void stubDelegate(GithubAllowlistOAuth2UserService service, OAuth2User user) {
        DefaultOAuth2UserService delegate = mock(DefaultOAuth2UserService.class);
        when(delegate.loadUser(any())).thenReturn(user);
        ReflectionTestUtils.setField(service, "delegate", delegate);
    }
}
