package com.tgrznar.javalearningapp.learning.coding;

/**
 * What a test case expects, detached from the database entity so the evaluation stays a pure
 * function. Never leaves the backend: the expected output must not reach the student.
 *
 * @param number         position of the case in the task, starting at 1
 * @param expectedOutput the output a correct solution prints
 * @param hidden         a hidden case shows the student only its number and verdict
 */
public record CaseExpectation(int number, String expectedOutput, boolean hidden) {

    /** Keeps the expected output out of logs. */
    @Override
    public String toString() {
        return "CaseExpectation[number=" + number + ", hidden=" + hidden + "]";
    }
}
