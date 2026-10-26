package com.tgrznar.javalearningapp.learning.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;

/**
 * The evaluation of one answer, shown after the submission so the student can learn from mistakes.
 * After a failed attempt the correct option is left out (null, so it is not in the JSON): the student
 * sees which answers were wrong, but cannot collect the correct answers by failing on purpose.
 * After a pass it is included, as a review.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QuizAnswerResult(
        Long questionId,
        QuizOption selected,
        QuizOption correctOption,
        boolean correct
) {

    /** The same evaluation without the correct option, for a failed attempt. */
    public QuizAnswerResult withoutCorrectOption() {
        return new QuizAnswerResult(questionId, selected, null, correct);
    }
}