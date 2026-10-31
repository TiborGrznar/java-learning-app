package com.tgrznar.javalearningapp.codingtask.dto;

import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;

/**
 * Administrator view of a test case, with the expected output. Students get a separate DTO
 * that never carries expectedOutput.
 */
public record CodingTaskCaseResponse(
        int number,
        String inputText,
        String expectedOutput,
        boolean hidden
) {

    public static CodingTaskCaseResponse from(CodingTaskCase testCase) {
        return new CodingTaskCaseResponse(
                testCase.getOrderNumber(),
                testCase.getInputText(),
                testCase.getExpectedOutput(),
                testCase.isHidden()
        );
    }
}
