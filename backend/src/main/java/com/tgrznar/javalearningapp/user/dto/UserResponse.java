package com.tgrznar.javalearningapp.user.dto;

import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;

import java.time.Instant;

/**
 * Public view of a user. Deliberately a separate type so that entity fields such as
 * passwordHash can never leak into an API response.
 */
public record UserResponse(
        Long id,
        String name,
        String surname,
        String email,
        UserRole role,
        Long schoolId,
        String schoolName,
        boolean active,
        Instant createdAt
) {

    /**
     * Maps an entity to the response. Touches the lazy School association,
     * so it must be called inside an open transaction (open-in-view is disabled).
     */
    public static UserResponse from(User user) {
        var school = user.getSchool();
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getSurname(),
                user.getEmail(),
                user.getRole(),
                school != null ? school.getId() : null,
                school != null ? school.getName() : null,
                user.isActive(),
                user.getCreatedAt()
        );
    }
}