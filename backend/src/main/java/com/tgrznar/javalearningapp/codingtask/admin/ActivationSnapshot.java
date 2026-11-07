package com.tgrznar.javalearningapp.codingtask.admin;

import java.util.List;

/**
 * A coding task as it was when its activation started: the reference solution, the driver and the cases
 * exactly as stored. The reference solution is verified against this snapshot, and once the verification
 * has passed, the activation is completed only if the stored task still equals it (record equality), so
 * an administrator editing the task during the verification cannot activate code that was never verified.
 * <p>
 * The snapshot holds code and expected outputs, so {@code toString} shows only ids and counts.
 */
public record ActivationSnapshot(
        Long moduleId,
        Long taskId,
        String referenceSolution,
        String driverCode,
        List<Case> cases) {

    /** One case in the order it is run. */
    public record Case(int number, String inputText, String expectedOutput) {

        @Override
        public String toString() {
            return "Case[number=" + number + "]";
        }
    }

    @Override
    public String toString() {
        return "ActivationSnapshot[taskId=" + taskId + ", cases=" + cases.size() + "]";
    }
}
