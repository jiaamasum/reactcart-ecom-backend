package org.masumjia.reactcartecom.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
    public static record RegisterRequest(@Email @NotBlank String email,
                                         @NotBlank String name,
                                         @NotBlank String password) {}

    public static record LoginRequest(@Email @NotBlank String email,
                                      @NotBlank String password) {}

    public static record TokenResponse(String accessToken) {}

    public static record SessionResponse(boolean authenticated, UserDto user) {}

    public static record UserDto(String id, String email, String name, String role, boolean isBanned,
                                 String phone, String address, String profileImageUrl, java.time.LocalDateTime createdAt) {}

    public static record RefreshRequest(@NotBlank String refreshToken) {}

    public static record LogoutRequest(String refreshToken) {}

    // Removed: forgot-password and change-with-old-password DTOs

    public static record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {}

    // New: reset by email with confirm password (no old password)
    public static record PasswordResetSimpleRequest(@Email @NotBlank String email,
                                                    @NotBlank String newPassword,
                                                    @NotBlank String confirmPassword) {}
}
