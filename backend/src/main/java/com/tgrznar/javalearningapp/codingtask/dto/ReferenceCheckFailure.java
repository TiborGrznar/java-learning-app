package com.tgrznar.javalearningapp.codingtask.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;

/**
 * Why a reference solution failed, for the administrator who wrote the task. Never used for students.
 *
 * @param status         COMPILE_ERROR, TIMEOUT, RUNTIME_ERROR or FAILED (the output differs from the expected one)
 * @param failedCase     number of the first case that failed; null when the code did not compile
 * @param passedCases    number of cases that passed
 * @param totalCases     number of cases of the task
 * @param message        the compiler message or what the runner reported for the failed case; null when there is none
 * @param expectedOutput the expected output of the failed case, only for status FAILED
 * @param actualOutput   what the reference solution printed in the failed case, only for status FAILED
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReferenceCheckFailure(
        CodingResultStatus status,
        Integer failedCase,
        int passedCases,
        int totalCases,
        String message,
        String expectedOutput,
        String actualOutput) {
}
