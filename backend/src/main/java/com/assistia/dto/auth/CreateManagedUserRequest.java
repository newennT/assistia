package com.assistia.dto.auth;

import com.assistia.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateManagedUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Size(max = 100) String department,
        @NotNull UserRole role,
        @NotBlank @Size(min = 12, max = 72) String password) {
}
