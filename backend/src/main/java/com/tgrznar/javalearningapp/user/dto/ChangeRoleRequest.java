package com.tgrznar.javalearningapp.user.dto;

import com.tgrznar.javalearningapp.user.model.UserRole;
import jakarta.validation.constraints.NotNull;

/** New role of a user. The schoolId is used only when the new role is TEACHER. */
public record ChangeRoleRequest(

        @NotNull(message = "Rola je povinná")
        UserRole role,

        Long schoolId
) {
}