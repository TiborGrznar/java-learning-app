package com.tgrznar.javalearningapp.learning.coding.dto;

import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskCase;

import java.util.List;

/**
 * A coding task as the student sees it: the assignment, the starting code and the visible example cases.
 * It never carries the driver code, the reference solution or anything about a hidden case except how many there are.
 *
 * @param hiddenCaseCount number of hidden cases, so the student knows the examples are not everything
 */
public record StudentCodingTaskDetail(
        Long id,
        int orderNumber,
        String title,
        String description,
        String templateCode,
        boolean solved,
        List<VisibleCase> visibleCases,
        int hiddenCaseCount) {

    /** An example the student may see completely. */
    public record VisibleCase(int number, String input, String expectedOutput) {
    }

    public static StudentCodingTaskDetail from(CodingTask task, List<CodingTaskCase> cases, boolean solved) {
        List<VisibleCase> visible = cases.stream()
                .filter(testCase -> !testCase.isHidden())
                .map(testCase -> new VisibleCase(testCase.getOrderNumber(), testCase.getInputText(), testCase.getExpectedOutput()))
                .toList();
        return new StudentCodingTaskDetail(
                task.getId(),
                task.getOrderNumber(),
                task.getTitle(),
                task.getDescription(),
                task.getTemplateCode(),
                solved,
                visible,
                cases.size() - visible.size());
    }
}
