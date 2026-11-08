package com.tgrznar.javalearningapp.learning.progress.dto;

import java.time.Instant;

/**
 * The latest quiz attempt of a student in a module (not the best one). It deliberately carries no
 * "passed" flag: the pass threshold is a setting that can change, so the verdict of an old attempt is
 * not recomputed. Whether the quiz was ever passed is {@link ModuleProgress#quizCompleted()}.
 */
public record LastQuizResult(int score, int maxScore, Instant completedAt) {
}
