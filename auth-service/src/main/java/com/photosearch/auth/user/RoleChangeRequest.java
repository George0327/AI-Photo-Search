package com.photosearch.auth.user;

import jakarta.validation.constraints.NotNull;

public record RoleChangeRequest(@NotNull UserRole role) {}
