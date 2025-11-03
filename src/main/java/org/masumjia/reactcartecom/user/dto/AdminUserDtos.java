package org.masumjia.reactcartecom.user.dto;

import jakarta.validation.constraints.*;

public class AdminUserDtos {
    public static record CreateUserRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 1, max = 100) String name,
            @NotBlank @Size(min = 6, max = 100) String password,
            @Pattern(regexp = "^(ADMIN|CUSTOMER)$", message = "Role must be ADMIN or CUSTOMER") String role,
            @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            @Size(max = 255) String address,
            @Size(max = 2048) String profileImageUrl,
            Boolean banned
    ) {}

    public static record UpdateUserRequest(
            @Size(min = 1, max = 100) String name,
            @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            @Size(max = 255) String address,
            @Size(max = 2048) String profileImageUrl,
            Boolean banned,
            @Pattern(regexp = "^(ADMIN|CUSTOMER)$", message = "Role must be ADMIN or CUSTOMER") String role,
            @Size(min = 6, max = 100) String newPassword
    ) {}

    public static record UserView(
            String id,
            String email,
            String name,
            String role,
            boolean banned,
            String phone,
            String address,
            String profileImageUrl,
            java.time.LocalDateTime createdAt,
            java.time.LocalDateTime updatedAt
    ) {}
}