package com.photosearch.auth.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GoogleDomainAccessService {

    @Value("${app.security.allowed-domain}")
    private String allowedDomain;

    public void validateAccess(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        Boolean emailVerified = oidcUser.getEmailVerified();
        String hostedDomain = oidcUser.getClaimAsString("hd");

        if (email == null || email.isBlank()) {
            throw accessDenied("Missing email in Google account.");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw accessDenied("Google email is not verified.");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String expectedSuffix = "@" + allowedDomain.trim().toLowerCase();

        if (!normalizedEmail.endsWith(expectedSuffix)) {
            throw accessDenied("Access is restricted to " + allowedDomain + " accounts.");
        }
        if (hostedDomain == null || !hostedDomain.equalsIgnoreCase(allowedDomain)) {
            throw accessDenied("Hosted domain is not allowed.");
        }
    }

    private OAuth2AuthenticationException accessDenied(String message) {
        OAuth2Error error = new OAuth2Error("access_denied", message, null);
        return new OAuth2AuthenticationException(error, message);
    }
}
