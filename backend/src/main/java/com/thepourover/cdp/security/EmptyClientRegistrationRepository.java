package com.thepourover.cdp.security;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

import java.util.Collections;
import java.util.Iterator;

/** Used when GitHub OAuth credentials are not configured. */
public final class EmptyClientRegistrationRepository
        implements ClientRegistrationRepository, Iterable<ClientRegistration> {

    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        return null;
    }

    @Override
    public Iterator<ClientRegistration> iterator() {
        return Collections.emptyIterator();
    }
}
