package com.photosearch.auth.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Map.of("authenticated", false, "sub", "", "email", "",
                    "name", "", "picture", "", "authorities", List.of());
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof OidcUser user)) {
            return Map.of("authenticated", false, "sub", "", "email", "",
                    "name", "", "picture", "", "authorities", List.of());
        }

        return Map.of(
                "authenticated", true,
                "sub", user.getSubject(),
                "email", user.getEmail(),
                "name", user.getFullName(),
                "picture", user.getPicture() != null ? user.getPicture() : "",
                "authorities", authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList()
        );
    }
}
