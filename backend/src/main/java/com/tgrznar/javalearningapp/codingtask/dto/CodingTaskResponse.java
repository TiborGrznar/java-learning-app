package com.tgrznar.javalearningapp.codingtask.dto;

import com.tgrznar.javalearningapp.codingtask.CodingTask;

/**
 * Full administrator view of a coding task, including the driver and the reference solution.
 * Students never get this type: their DTOs must not carry driverCode, referenceSolution or
 * the expected outputs.
 */
public record CodingTaskResponse(
        Long id,
        Long moduleId,
        int orderNumber,
        String title,
        String description,
        String templateCode,
        String driverCode,
        String referenceSolution,
        boolean active,
        long caseCount
) {

    public static CodingTaskResponse from(CodingTask task, Long moduleId, long caseCount) {
        return new CodingTaskResponse(
                task.getId(),
                moduleId,
                task.getOrderNumber(),
                task.getTitle(),
                task.getDescription(),
                task.getTemplateCode(),
                task.getDriverCode(),
                task.getReferenceSolution(),
                task.isActive(),
                caseCount
        );
    }
}
