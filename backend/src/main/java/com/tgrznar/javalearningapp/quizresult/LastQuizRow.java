package com.tgrznar.javalearningapp.quizresult;

import java.time.Instant;

/**
 * The latest quiz result of one student in one module, read straight from the database.
 * Wrapper types, because the JPQL constructor expression that fills it is the safest with them.
 */
public record LastQuizRow(Long moduleId, Integer score, Integer maxScore, Instant completedAt) {
}
