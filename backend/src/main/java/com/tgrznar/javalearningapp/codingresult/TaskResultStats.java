package com.tgrznar.javalearningapp.codingresult;

import java.time.Instant;

/**
 * What one student's results of one coding task add up to: how many of them passed and when the last one
 * was stored. Only numbers and a time, never code or output.
 */
public record TaskResultStats(Long taskId, Long passedCount, Instant lastAttemptAt) {
}
