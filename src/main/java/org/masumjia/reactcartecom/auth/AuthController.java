package org.masumjia.reactcartecom.auth;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.masumjia.reactcartecom.common.ApiResponse;
import org.masumjia.reactcartecom.user.User;
import org.masumjia.reactcartecom.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.masumjia.reactcartecom.auth.dto.AuthDtos.*;

import org.masumjia.reactcartecom.security.JwtService;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user", security = {})
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@Valid @RequestBody RegisterRequest req) {
        if (users.findByEmail(req.email()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }
        User u = new User();
        u.setId(UUID.randomUUID().toString());
        u.setEmail(req.email());
        u.setName(req.name());
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setRole("CUSTOMER");
        u.setBanned(false);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);

        var payload = tokensPayload(u);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(payload, java.util.Map.of("message", "Registration successful")));
    }

    @PostMapping("/login")
    @Operation(summary = "Login and receive access token", security = {})
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@Valid @RequestBody LoginRequest req) {
        User u = users.findByEmail(req.email()).orElseThrow(() -> new IllegalArgumentException("Email not registered"));
        if (u.isBanned()) {
            throw new IllegalArgumentException("Account is banned");
        }
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password");
        }
        var payload = tokensPayload(u);
        return ResponseEntity.ok(ApiResponse.success(payload, java.util.Map.of("message", "Login successful")));
    }

    // Refresh endpoint removed per requirement

    @PostMapping("/logout")
    @Operation(summary = "Logout (client should discard token)", security = {})
    public ResponseEntity<Void> logout() {
        // Stateless JWT: client discards access token; nothing to do server-side
        return ResponseEntity.noContent().build();
    }

    // Removed: /forgot-password (change with old password) per requirement

    // Reset password by email with confirm password (no old password)
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password with email + confirm", security = {})
    public ResponseEntity<ApiResponse<Object>> resetPasswordSimple(@Valid @RequestBody PasswordResetSimpleRequest req) {
        if (!req.newPassword().equals(req.confirmPassword())) {
            throw new IllegalArgumentException("Password and confirm password do not match");
        }
        User u = users.findByEmail(req.email()).orElseThrow(() -> new IllegalArgumentException("User not found"));
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        u.setUpdatedAt(LocalDateTime.now());
        users.save(u);
        return ResponseEntity.ok(ApiResponse.success(null, java.util.Map.of("message", "Password reset successful")));
    }

    // Session endpoint removed per requirement

    private Map<String, Object> tokensPayload(User u) {
        String access = jwtService.generateAccess(u.getId(), Map.of("role", u.getRole()));
        UserDto dto = new UserDto(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.isBanned(), u.getPhone(), u.getAddress(), u.getProfileImageUrl(), u.getCreatedAt());
        return Map.of("user", dto, "accessToken", access);
    }
}
