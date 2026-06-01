package com.photosearch.auth.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'OWNER')")
    public List<UserDto> listUsers() {
        return userAdminService.listAllUsers()
                .stream()
                .map(UserDto::from)
                .toList();
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
