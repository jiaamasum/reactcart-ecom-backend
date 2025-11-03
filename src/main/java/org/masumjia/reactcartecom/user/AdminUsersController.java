package org.masumjia.reactcartecom.user;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.common.ApiError;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.user.dto.AdminUserDtos;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@Tag(name = "Admin Users")
public class AdminUsersController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminUsersController(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Operation(summary = "List users (admins + customers)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<List<AdminUserDtos.UserView>>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search
    ) {
        List<AdminUserDtos.UserView> data = users.findAll().stream()
                .filter(u -> role == null || role.isBlank() || role.equalsIgnoreCase(u.getRole()))
                .filter(u -> {
                    if (search == null || search.isBlank()) return true;
                    String s = search.toLowerCase();
                    return (u.getEmail() != null && u.getEmail().toLowerCase().contains(s)) ||
                           (u.getName() != null && u.getName().toLowerCase().contains(s));
                })
                .map(this::toView)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, Map.of("count", data.size())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<AdminUserDtos.UserView>> get(@PathVariable String id) {
        return users.findById(id)
                .map(u -> ResponseEntity.ok(ApiResponse.success(toView(u))))
                .orElse(ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    @PostMapping
    @Operation(summary = "Create a user (admin or customer)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<AdminUserDtos.UserView>> create(@Valid @RequestBody AdminUserDtos.CreateUserRequest req) {
        if (users.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User u = new User();
        u.setId(java.util.UUID.randomUUID().toString());
        u.setEmail(req.email());
        u.setName(req.name());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        String role = req.role() != null ? req.role() : "CUSTOMER";
        u.setRole(role);
        u.setBanned(Boolean.TRUE.equals(req.banned()));
        u.setPhone(req.phone());
        u.setAddress(req.address());
        u.setProfileImageUrl(req.profileImageUrl());
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
        return ResponseEntity.status(201).body(ApiResponse.success(toView(u), Map.of("message", "User created")));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user (partial)", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<AdminUserDtos.UserView>> update(@PathVariable String id,
                                                                      @Valid @RequestBody AdminUserDtos.UpdateUserRequest req) {
        return users.findById(id).map(u -> {
            if (req.name() != null) u.setName(req.name());
            if (req.phone() != null) u.setPhone(req.phone());
            if (req.address() != null) u.setAddress(req.address());
            if (req.profileImageUrl() != null) u.setProfileImageUrl(req.profileImageUrl());
            if (req.banned() != null) u.setBanned(req.banned());
            if (req.role() != null) u.setRole(req.role());
            if (req.newPassword() != null) u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
            u.setUpdatedAt(LocalDateTime.now());
            users.save(u);
            return ResponseEntity.ok(ApiResponse.success(toView(u), Map.of("message", "User updated")));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    @PostMapping("/{id}/ban")
    @Operation(summary = "Ban a user", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> ban(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.setBanned(true);
            u.setUpdatedAt(LocalDateTime.now());
            users.save(u);
            return ResponseEntity.ok(ApiResponse.success((Object)java.util.Map.of("id", u.getId(), "banned", true), java.util.Map.of("message", "User banned")));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    @PostMapping("/{id}/unban")
    @Operation(summary = "Unban a user", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> unban(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.setBanned(false);
            u.setUpdatedAt(LocalDateTime.now());
            users.save(u);
            return ResponseEntity.ok(ApiResponse.success((Object)java.util.Map.of("id", u.getId(), "banned", false), java.util.Map.of("message", "User unbanned")));
        }).orElseGet(() -> ResponseEntity.status(404)
                .body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    
    @PostMapping("/{id}/promote")
    @Operation(summary = "Promote user to admin", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> promote(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.setRole("ADMIN");
            u.setUpdatedAt(LocalDateTime.now());
            users.save(u);
            return ResponseEntity.ok(ApiResponse.success((Object)java.util.Map.of("id", u.getId(), "role", u.getRole()), java.util.Map.of("message", "User promoted to admin")));
        }).orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }

    @PostMapping("/{id}/demote")
    @Operation(summary = "Demote user to customer", security = { @SecurityRequirement(name = "bearerAuth") })
    public ResponseEntity<ApiResponse<Object>> demote(@PathVariable String id) {
        return users.findById(id).map(u -> {
            u.setRole("CUSTOMER");
            u.setUpdatedAt(LocalDateTime.now());
            users.save(u);
            return ResponseEntity.ok(ApiResponse.success((Object)java.util.Map.of("id", u.getId(), "role", u.getRole()), java.util.Map.of("message", "User demoted to customer")));
        }).orElseGet(() -> ResponseEntity.status(404).body(ApiResponse.error(new ApiError("NOT_FOUND", "User not found"))));
    }private AdminUserDtos.UserView toView(User u) {
        return new AdminUserDtos.UserView(
                u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isBanned(), u.getPhone(), u.getAddress(), u.getProfileImageUrl(), u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}