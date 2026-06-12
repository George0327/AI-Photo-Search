package com.photosearch.auth.user;

import java.util.List;

public record UserPageResponse(List<UserDto> users, long totalUsers, int page, int size) {}
