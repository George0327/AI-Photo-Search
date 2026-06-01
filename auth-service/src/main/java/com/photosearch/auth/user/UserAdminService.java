package com.photosearch.auth.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User changeUserRole(Long targetUserId, UserRole newRole) {
        User requester = resolveRequester();
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (requester.getId().equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change your own role.");
        }
        if (!requester.getRole().canAffect(target.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot modify a user with equal or higher rank.");
        }
        if (!requester.getRole().canAffect(newRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You cannot assign a role equal to or above your own rank.");
        }

        target.setRole(newRole);
        return userRepository.save(target);
    }

    private User resolveRequester() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidcUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return userRepository.findByGoogleSub(oidcUser.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Requester not found in database"));
    }
}
