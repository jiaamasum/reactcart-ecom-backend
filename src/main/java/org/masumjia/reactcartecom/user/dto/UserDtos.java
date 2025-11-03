package org.masumjia.reactcartecom.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UserDtos {
    // All fields optional for PATCH; constraints apply only when provided
    public static record UpdateUserRequest(
            @Size(min = 1, max = 100, message = "Name must be 1-100 characters") String name,
            @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Invalid phone number") String phone,
            @Size(max = 255, message = "Address too long") String address,
            @Size(max = 2048, message = "Profile image URL too long") String profileImageUrl
    ) {}
}

