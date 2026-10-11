package com.tgrznar.javalearningapp.module.dto;

import com.tgrznar.javalearningapp.module.CourseModule;

import java.time.Instant;

/** Row of the administrator's module list, without the (possibly long) theory. */
public record ModuleSummary(
        Long id,
        String title,
        String description,
        int orderNumber,
        boolean active,
        Instant createdAt
) {

    public static ModuleSummary from(CourseModule module) {
        return new ModuleSummary(
                module.getId(),
                module.getTitle(),
                module.getDescription(),
                module.getOrderNumber(),
                module.isActive(),
                module.getCreatedAt()
        );
    }
}