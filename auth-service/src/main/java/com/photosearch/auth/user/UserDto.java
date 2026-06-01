package com.photosearch.auth.user;

import java.time.Instant;

public record UserDto(
        Long id,
        String email,
        String fullName,
        String profilePictureUrl,
        UserRole role,
        boolean active,
        Instant createdAt
) {
    public static UserDto from(User user) {
        return new UserDto(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getProfilePictureUrl(),
                user.getRole(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
