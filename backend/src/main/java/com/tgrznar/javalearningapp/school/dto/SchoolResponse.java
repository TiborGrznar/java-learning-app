package com.tgrznar.javalearningapp.school.dto;

import com.tgrznar.javalearningapp.school.School;

import java.time.Instant;

public record SchoolResponse(
        Long id,
        String name,
        String address,
        boolean active,
        Instant createdAt
) {
    public static SchoolResponse from(School school) {
        return new SchoolResponse(
                school.getId(),
                school.getName(),
                school.getAddress(),
                school.isActive(),
                school.getCreatedAt());
    }
}