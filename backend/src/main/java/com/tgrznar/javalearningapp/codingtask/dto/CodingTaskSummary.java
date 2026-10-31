package com.tgrznar.javalearningapp.codingtask.dto;

import com.tgrznar.javalearningapp.codingtask.CodingTask;

/** One row of the administrator's task list, without any code. */
public record CodingTaskSummary(
        Long id,
        int orderNumber,
        String title,
        boolean active,
        long caseCount
) {

    public static CodingTaskSummary from(CodingTask task, long caseCount) {
        return new CodingTaskSummary(
                task.getId(),
                task.getOrderNumber(),
                task.getTitle(),
                task.isActive(),
                caseCount
        );
    }
}
