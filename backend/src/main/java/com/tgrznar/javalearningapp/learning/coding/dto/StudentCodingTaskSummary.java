package com.tgrznar.javalearningapp.learning.coding.dto;

import com.tgrznar.javalearningapp.codingtask.CodingTask;

/** One row of the student's list of coding tasks of a module. */
public record StudentCodingTaskSummary(Long id, int orderNumber, String title, boolean solved) {

    public static StudentCodingTaskSummary from(CodingTask task, boolean solved) {
        return new StudentCodingTaskSummary(task.getId(), task.getOrderNumber(), task.getTitle(), solved);
    }
}
