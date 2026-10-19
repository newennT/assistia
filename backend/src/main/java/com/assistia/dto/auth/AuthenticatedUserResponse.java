package com.assistia.dto.auth;

import com.assistia.entity.User;
import com.assistia.entity.UserRole;

import java.time.LocalDateTime;

public record AuthenticatedUserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        String department,
        LocalDateTime createdAt) {

    public static AuthenticatedUserResponse from(User user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getDepartment(),
                user.getCreatedAt());
    }
}
