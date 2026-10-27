package com.tgrznar.javalearningapp.learning.quiz;

import java.util.List;

/**
 * What a valid quiz token carries: its unique id (stored with the result, so the token works once)
 * and the ids of the questions the student was shown, in the order they were shown.
 */
public record VerifiedQuizToken(String id, List<Long> questionIds) {
}