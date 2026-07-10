package com.photosearch.auth.auth;

import com.photosearch.auth.user.User;
import com.photosearch.auth.user.UserRepository;
import com.photosearch.auth.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private final GoogleDomainAccessService googleDomainAccessService;
    private final UserRepository userRepository;

    // Bootstrap: a configured email is auto-promoted to OWNER on login (the first OWNER cannot be created in-app). Blank disables it.
    @Value("${app.security.owner-email:}")
    private String ownerEmail;

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        googleDomainAccessService.validateAccess(oidcUser);

        User user = upsertUser(oidcUser);

        Set<GrantedAuthority> mappedAuthorities = new HashSet<>(oidcUser.getAuthorities());
        mappedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        return new DefaultOidcUser(
                mappedAuthorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
    }

    private User upsertUser(OidcUser oidcUser) {
        String googleSub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String fullName = oidcUser.getFullName();
        String pictureUrl = oidcUser.getPicture();

        User user = userRepository.findByGoogleSub(googleSub)
                .map(existing -> updateExistingUser(existing, email, fullName, pictureUrl))
                .orElseGet(() -> createNewUser(googleSub, email, fullName, pictureUrl));

        return ensureBootstrapOwner(user);
    }

    private User ensureBootstrapOwner(User user) {
        if (ownerEmail != null && !ownerEmail.isBlank()
                && ownerEmail.trim().equalsIgnoreCase(user.getEmail())
                && user.getRole() != UserRole.OWNER) {
            user.setRole(UserRole.OWNER);
            return userRepository.save(user);
        }
        return user;
    }

    private User updateExistingUser(User user, String email, String fullName, String pictureUrl) {
        user.setEmail(email);
        user.setFullName(fullName);
        user.setProfilePictureUrl(pictureUrl);
        user.setActive(true);
        return userRepository.save(user);
    }

    private User createNewUser(String googleSub, String email, String fullName, String pictureUrl) {
        User user = User.builder()
                .googleSub(googleSub)
                .email(email)
                .fullName(fullName)
                .profilePictureUrl(pictureUrl)
                .role(UserRole.USER)
                .active(true)
                .build();
        return userRepository.save(user);
    }
}
