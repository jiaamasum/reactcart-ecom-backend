package org.masumjia.reactcartecom.user;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/user-details")
@Tag(name = "User Details")
public class UserDetailsController {

    private final UserRepository users;

    public UserDetailsController(UserRepository users) {
        this.users = users;
    }

    // Get all information about the currently logged-in user
    @GetMapping
    @Operation(summary = "Get current user details", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<User>> getDetails(Authentication auth) {
        return users.findById(auth.getName())
                .map(u -> ResponseEntity.ok(ApiResponse.success(u, java.util.Map.of("message", "Fetched user details"))))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    // Update the current user's details (validated DTO; all fields optional)
    @PatchMapping
    @Operation(summary = "Update current user details", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<User>> updateDetails(Authentication auth,
                                                           @Valid @RequestBody org.masumjia.reactcartecom.user.dto.UserDtos.UpdateUserRequest body) {
        User u = users.findById(auth.getName()).orElseThrow();
        if (body.name() != null) u.setName(body.name());
        if (body.phone() != null) u.setPhone(body.phone());
        if (body.address() != null) u.setAddress(body.address());
        if (body.profileImageUrl() != null) u.setProfileImageUrl(body.profileImageUrl());
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
        return ResponseEntity.ok(ApiResponse.success(u, java.util.Map.of("message", "Updated user details")));
    }

    // PUT alias for frontend preference (same body as PATCH)
    @PutMapping
    @Operation(summary = "Replace current user details", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<User>> putDetails(Authentication auth,
                                                        @Valid @RequestBody org.masumjia.reactcartecom.user.dto.UserDtos.UpdateUserRequest body) {
        return updateDetails(auth, body);
    }
}
