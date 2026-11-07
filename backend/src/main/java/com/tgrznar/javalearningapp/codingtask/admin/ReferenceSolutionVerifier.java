package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.coderunner.CodeRunner;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.dto.ReferenceCheckFailure;
import com.tgrznar.javalearningapp.codingtask.exception.ReferenceSolutionFailedException;
import com.tgrznar.javalearningapp.learning.coding.AttemptEvaluation;
import com.tgrznar.javalearningapp.learning.coding.AttemptEvaluator;
import com.tgrznar.javalearningapp.learning.coding.CaseExpectation;
import com.tgrznar.javalearningapp.learning.coding.CaseOutcome;
import com.tgrznar.javalearningapp.learning.coding.CaseVerdict;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Proves that a coding task can be solved: runs its reference solution in the sandbox over all of its cases and
 * compares the output with the expected output by the same rules students are judged by ({@link AttemptEvaluator}).
 * <p>
 * The reference solution is admin-written code, but it goes through the same sandbox and limits as student code.
 * Every case is evaluated as a visible one, because the diagnostics go to the administrator who wrote the task
 * and has to see what went wrong (the full message of the runner, the expected and the actual output). This
 * class must therefore never be used for a student's submission.
 * <p>
 * Like a submission, this must not run inside a database transaction: one call can last more than a minute.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReferenceSolutionVerifier {

    /** A longer text is cut: the runner already limits output, this keeps the error answer small. */
    static final int MAX_TEXT_LENGTH = 2_000;

    private final CodeRunner codeRunner;
    private final AttemptEvaluator evaluator;

    /**
     * @throws ReferenceSolutionFailedException the reference solution did not pass every case
     * @throws com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException        the runner has no free slot
     * @throws com.tgrznar.javalearningapp.coderunner.CodeRunnerUnavailableException the runner failed
     */
    public void verify(ActivationSnapshot snapshot) {
        List<String> inputs = snapshot.cases().stream().map(ActivationSnapshot.Case::inputText).toList();
        List<CaseExpectation> expectations = snapshot.cases().stream()
                .map(testCase -> new CaseExpectation(testCase.number(), testCase.expectedOutput(), false))
                .toList();

        RunnerResponse response = codeRunner.run(snapshot.referenceSolution(), snapshot.driverCode(), inputs);
        AttemptEvaluation evaluation = evaluator.evaluate(response, expectations, lineCount(snapshot.referenceSolution()));

        if (evaluation.passed()) {
            log.info("Reference solution verified: taskId={}, cases={}", snapshot.taskId(), evaluation.totalCases());
            return;
        }
        ReferenceCheckFailure failure = failureOf(snapshot, evaluation);
        log.warn("Reference solution failed: taskId={}, status={}, failedCase={}",
                snapshot.taskId(), failure.status(), failure.failedCase());
        throw new ReferenceSolutionFailedException(snapshot.taskId(), failure);
    }

    private static ReferenceCheckFailure failureOf(ActivationSnapshot snapshot, AttemptEvaluation evaluation) {
        CaseOutcome failedCase = evaluation.cases().stream()
                .filter(outcome -> outcome.verdict() != CaseVerdict.PASSED)
                .findFirst()
                .orElse(null);
        if (failedCase == null) {
            // The code did not compile (or the compilation timed out): there are no cases.
            return new ReferenceCheckFailure(evaluation.status(), null, evaluation.passedCases(),
                    evaluation.totalCases(), cut(evaluation.errorMessage()), null, null);
        }
        String expectedOutput = null;
        if (evaluation.status() == CodingResultStatus.FAILED) {
            expectedOutput = snapshot.cases().stream()
                    .filter(testCase -> testCase.number() == failedCase.number())
                    .map(ActivationSnapshot.Case::expectedOutput)
                    .findFirst()
                    .orElse(null);
        }
        return new ReferenceCheckFailure(evaluation.status(), failedCase.number(), evaluation.passedCases(),
                evaluation.totalCases(), cut(failedCase.message()), cut(expectedOutput), cut(failedCase.actualOutput()));
    }

    private static String cut(String text) {
        if (text == null || text.length() <= MAX_TEXT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_TEXT_LENGTH) + "…";
    }

    private static int lineCount(String code) {
        return (int) code.lines().count();
    }
}
