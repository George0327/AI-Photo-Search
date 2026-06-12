package com.photosearch.auth.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private static final int MAX_PAGE_SIZE = 200;

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OWNER')")
    public UserPageResponse listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Page<User> users = userAdminService.listUsers(safePage, safeSize);
        return new UserPageResponse(
                users.getContent().stream().map(UserDto::from).toList(),
                users.getTotalElements(), safePage, safeSize);
    }

    @PatchMapping("/{userId}/role")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OWNER')")
    public UserDto changeRole(
            @PathVariable Long userId,
            @RequestBody @Valid RoleChangeRequest request
    ) {
        return UserDto.from(userAdminService.changeUserRole(userId, request.role()));
    }
}
