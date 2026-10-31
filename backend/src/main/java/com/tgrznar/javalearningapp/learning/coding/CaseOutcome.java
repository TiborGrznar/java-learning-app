package com.tgrznar.javalearningapp.learning.coding;

/**
 * Verdict on one case as the student may see it.
 *
 * @param number       position of the case in the task, starting at 1
 * @param hidden       whether the case is hidden
 * @param verdict      the verdict
 * @param message      explanation of a runtime error or timeout; always null for a hidden case
 * @param actualOutput what the program printed when a visible case failed; null otherwise and always
 *                     null for a hidden case
 */
public record CaseOutcome(int number, boolean hidden, CaseVerdict verdict, String message, String actualOutput) {
}
