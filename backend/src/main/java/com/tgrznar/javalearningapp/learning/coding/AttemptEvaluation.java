package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;

import java.util.List;

/**
 * Verdict on a whole submission.
 *
 * @param status       PASSED when every case passed, otherwise the reason of the first failure
 * @param passedCases  number of passed cases
 * @param totalCases   number of cases of the task
 * @param errorMessage what the student may read about the failure: the compiler message, or the message
 *                     of the first failing visible case; null when there is nothing to show
 * @param actualOutput output of the first failing visible case that ran; null otherwise
 * @param cases        outcomes per case, empty when the code did not compile
 */
public record AttemptEvaluation(
        CodingResultStatus status,
        int passedCases,
        int totalCases,
        String errorMessage,
        String actualOutput,
        List<CaseOutcome> cases) {

    public boolean passed() {
        return status == CodingResultStatus.PASSED;
    }
}
