package com.thepourover.cdp.security;

import com.thepourover.cdp.config.CdpProperties;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SharedPasswordAuthenticationProvider implements AuthenticationProvider {

    private final CdpProperties properties;

    public SharedPasswordAuthenticationProvider(CdpProperties properties) {
        this.properties = properties;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String password = String.valueOf(authentication.getCredentials());
        if (!properties.uiPassword().equals(password)) {
            throw new BadCredentialsException("Invalid password");
        }
        return UsernamePasswordAuthenticationToken.authenticated(
                "growth",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
