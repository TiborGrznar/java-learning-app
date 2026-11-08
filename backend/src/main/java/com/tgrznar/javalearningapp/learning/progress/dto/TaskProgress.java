package com.tgrznar.javalearningapp.learning.progress.dto;

import java.time.Instant;

/**
 * One active coding task of a module in the student's progress.
 *
 * @param lastAttemptAt when the student last submitted the task; null when {@code state} is NOT_ATTEMPTED
 */
public record TaskProgress(Long taskId, int orderNumber, String title, TaskState state, Instant lastAttemptAt) {
}
