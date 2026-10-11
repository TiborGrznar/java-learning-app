package com.tgrznar.javalearningapp.module.dto;

import com.tgrznar.javalearningapp.module.CourseModule;

import java.time.Instant;

/** Full administrator view of a module, including the theory. */
public record ModuleResponse(
        Long id,
        String title,
        String description,
        String theoryContent,
        int orderNumber,
        boolean active,
        Instant createdAt
) {

    public static ModuleResponse from(CourseModule module) {
        return new ModuleResponse(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getTheoryContent(),
                module.getOrderNumber(),
                module.isActive(),
                module.getCreatedAt()
        );
    }
}