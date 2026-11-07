package com.tgrznar.javalearningapp.learning.coding.dto;

import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.learning.coding.AttemptEvaluation;
import com.tgrznar.javalearningapp.learning.coding.CaseOutcome;

import java.util.List;

/**
 * The verdict on a submission. Everything in it is safe to show: {@link AttemptEvaluation} already holds
 * back what a hidden case must not reveal, and expected outputs are never part of it.
 *
 * @param status                 PASSED, or the reason of the first failure
 * @param passed                 true when every case passed
 * @param passedCases            number of passed cases
 * @param totalCases             number of cases of the task
 * @param errorMessage           compiler message or the message of the first failing case; null when none
 * @param actualOutput           what the program printed in the first failing visible case; null otherwise
 * @param cases                  outcome per case, empty when the code did not compile
 * @param moduleCodingCompleted  true when this submission completed the coding part of the module, or it was completed before
 */
public record CodingSubmissionResponse(
        CodingResultStatus status,
        boolean passed,
        int passedCases,
        int totalCases,
        String errorMessage,
        String actualOutput,
        List<CaseOutcome> cases,
        boolean moduleCodingCompleted) {

    public static CodingSubmissionResponse from(AttemptEvaluation evaluation, boolean moduleCodingCompleted) {
        return new CodingSubmissionResponse(
                evaluation.status(),
                evaluation.passed(),
                evaluation.passedCases(),
                evaluation.totalCases(),
                evaluation.errorMessage(),
                evaluation.actualOutput(),
                evaluation.cases(),
                moduleCodingCompleted);
    }
}
