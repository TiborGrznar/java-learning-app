package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the answer of the code runner into a verdict. The comparison with the expected output happens
 * here, in the backend: the runner never learns what the expected output is.
 * <p>
 * Overall status: PASSED when every case passes; COMPILE_ERROR when the code did not compile (TIMEOUT
 * when the compilation itself timed out); otherwise the status of the first failing case in the order
 * of the cases (TIMEOUT, RUNTIME_ERROR or FAILED). Running out of memory counts as a runtime error.
 * <p>
 * What the student may see is decided here: a hidden case yields only its number and verdict, never a
 * message or an output, and nothing derived from a hidden case ends up in {@link AttemptEvaluation#errorMessage()}
 * or {@link AttemptEvaluation#actualOutput()}.
 */
@Component
public class AttemptEvaluator {

    static final String COMPILE_TIMEOUT_MESSAGE = "Kompilácia trvala príliš dlho";
    static final String CASE_TIMEOUT_MESSAGE = "Program bežal príliš dlho";
    static final String OUT_OF_MEMORY_MESSAGE = "Program vyčerpal pamäť, ktorú mal k dispozícii";
    static final String RUNTIME_ERROR_MESSAGE = "Program skončil chybou";

    /**
     * @param response the answer of the runner, already verified by the runner client
     * @param expected the cases of the task, in order, numbered 1..n
     * @throws IllegalArgumentException when a successful compilation does not come with exactly one result per case
     */
    public AttemptEvaluation evaluate(RunnerResponse response, List<CaseExpectation> expected) {
        int total = expected.size();
        RunnerResponse.Compile compile = response.compile();
        if (!compile.success()) {
            return compileFailure(compile, total);
        }
        List<RunnerResponse.CaseResult> results = response.cases();
        if (results.size() != total) {
            throw new IllegalArgumentException("Expected " + total + " case results but got " + results.size());
        }

        List<CaseOutcome> outcomes = new ArrayList<>(total);
        int passed = 0;
        CodingResultStatus status = CodingResultStatus.PASSED;
        String errorMessage = null;
        String actualOutput = null;
        for (int i = 0; i < total; i++) {
            CaseExpectation expectation = expected.get(i);
            RunnerResponse.CaseResult result = results.get(i);
            if (result.number() != expectation.number()) {
                throw new IllegalArgumentException("Case result " + result.number() + " does not match case " + expectation.number());
            }
            CaseOutcome outcome = outcomeOf(expectation, result);
            outcomes.add(outcome);
            if (outcome.verdict() == CaseVerdict.PASSED) {
                passed++;
            } else if (status == CodingResultStatus.PASSED) {
                status = statusOf(outcome.verdict());
                errorMessage = outcome.message();
                actualOutput = outcome.actualOutput();
            }
        }
        return new AttemptEvaluation(status, passed, total, errorMessage, actualOutput, List.copyOf(outcomes));
    }

    private static AttemptEvaluation compileFailure(RunnerResponse.Compile compile, int total) {
        if (compile.timedOut()) {
            return new AttemptEvaluation(CodingResultStatus.TIMEOUT, 0, total, COMPILE_TIMEOUT_MESSAGE, null, List.of());
        }
        String message = compile.message() == null || compile.message().isBlank()
                ? "Kód sa nepodarilo skompilovať"
                : compile.message();
        return new AttemptEvaluation(CodingResultStatus.COMPILE_ERROR, 0, total, message, null, List.of());
    }

    private static CaseOutcome outcomeOf(CaseExpectation expectation, RunnerResponse.CaseResult result) {
        CaseVerdict verdict;
        String message = null;
        String actualOutput = null;
        switch (result.status()) {
            case OK -> {
                String stdout = result.stdout() == null ? "" : result.stdout();
                if (OutputComparator.matches(expectation.expectedOutput(), stdout)) {
                    verdict = CaseVerdict.PASSED;
                } else {
                    verdict = CaseVerdict.FAILED;
                    actualOutput = stdout;
                }
            }
            case TIMEOUT -> {
                verdict = CaseVerdict.TIMEOUT;
                message = CASE_TIMEOUT_MESSAGE;
            }
            case OUT_OF_MEMORY -> {
                verdict = CaseVerdict.RUNTIME_ERROR;
                message = OUT_OF_MEMORY_MESSAGE;
            }
            case RUNTIME_ERROR -> {
                verdict = CaseVerdict.RUNTIME_ERROR;
                message = result.message() == null || result.message().isBlank() ? RUNTIME_ERROR_MESSAGE : result.message();
            }
            default -> throw new IllegalArgumentException("Unknown case status " + result.status());
        }
        if (expectation.hidden()) {
            return new CaseOutcome(expectation.number(), true, verdict, null, null);
        }
        return new CaseOutcome(expectation.number(), false, verdict, message, actualOutput);
    }

    private static CodingResultStatus statusOf(CaseVerdict verdict) {
        return switch (verdict) {
            case FAILED -> CodingResultStatus.FAILED;
            case RUNTIME_ERROR -> CodingResultStatus.RUNTIME_ERROR;
            case TIMEOUT -> CodingResultStatus.TIMEOUT;
            case PASSED -> CodingResultStatus.PASSED;
        };
    }
}
