package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseResult;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.Compile;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttemptEvaluatorTest {

    private final AttemptEvaluator evaluator = new AttemptEvaluator();

    private static final Compile COMPILED = new Compile(true, false, "");

    private static CaseExpectation visible(int number, String expected) {
        return new CaseExpectation(number, expected, false);
    }

    private static CaseExpectation hidden(int number, String expected) {
        return new CaseExpectation(number, expected, true);
    }

    private static CaseResult ok(int number, String stdout) {
        return new CaseResult(number, CaseStatus.OK, 10, stdout, "");
    }

    private static CaseResult failing(int number, CaseStatus status, String message) {
        return new CaseResult(number, status, 10, null, message);
    }

    private static RunnerResponse answer(CaseResult... results) {
        return new RunnerResponse(COMPILED, List.of(results));
    }

    // ---------- passing ----------

    @Test
    void everyCasePasses_statusIsPassed() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "9\n"), ok(2, "16\n")),
                List.of(visible(1, "9"), hidden(2, "16")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.PASSED);
        assertThat(evaluation.passed()).isTrue();
        assertThat(evaluation.passedCases()).isEqualTo(2);
        assertThat(evaluation.totalCases()).isEqualTo(2);
        assertThat(evaluation.errorMessage()).isNull();
        assertThat(evaluation.actualOutput()).isNull();
        assertThat(evaluation.cases()).extracting(CaseOutcome::verdict).containsOnly(CaseVerdict.PASSED);
    }

    @Test
    void okWithoutStdout_countsAsEmptyOutput() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(new CaseResult(1, CaseStatus.OK, 1, null, "")),
                List.of(visible(1, "")));

        assertThat(evaluation.passed()).isTrue();
    }

    // ---------- wrong output ----------

    @Test
    void wrongOutput_statusIsFailedAndVisibleCaseShowsWhatWasPrinted() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "8\n"), ok(2, "16\n")),
                List.of(visible(1, "9"), visible(2, "16")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(evaluation.passedCases()).isEqualTo(1);
        assertThat(evaluation.actualOutput()).isEqualTo("8\n");
        assertThat(evaluation.cases().get(0).verdict()).isEqualTo(CaseVerdict.FAILED);
        assertThat(evaluation.cases().get(0).actualOutput()).isEqualTo("8\n");
        assertThat(evaluation.cases().get(1).verdict()).isEqualTo(CaseVerdict.PASSED);
    }

    @Test
    void outputDifferingOnlyInIgnoredWhitespace_passes() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "a  \r\nb\r\n\r\n")),
                List.of(visible(1, "a\nb")));

        assertThat(evaluation.passed()).isTrue();
    }

    // ---------- hidden cases ----------

    @Test
    void hiddenFailingCase_revealsOnlyNumberAndVerdict() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "wrong secret\n")),
                List.of(hidden(1, "expected secret")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.FAILED);
        CaseOutcome outcome = evaluation.cases().get(0);
        assertThat(outcome.hidden()).isTrue();
        assertThat(outcome.verdict()).isEqualTo(CaseVerdict.FAILED);
        assertThat(outcome.message()).isNull();
        assertThat(outcome.actualOutput()).isNull();
        assertThat(evaluation.errorMessage()).isNull();
        assertThat(evaluation.actualOutput()).isNull();
    }

    @Test
    void hiddenRuntimeError_revealsNoMessage() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.RUNTIME_ERROR, "Exception in thread main: secret detail")),
                List.of(hidden(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.RUNTIME_ERROR);
        assertThat(evaluation.cases().get(0).message()).isNull();
        assertThat(evaluation.errorMessage()).isNull();
    }

    // ---------- runtime errors, timeouts, memory ----------

    @Test
    void visibleRuntimeError_showsTheMessage() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.RUNTIME_ERROR, "java.lang.ArithmeticException: / by zero")),
                List.of(visible(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.RUNTIME_ERROR);
        assertThat(evaluation.errorMessage()).isEqualTo("java.lang.ArithmeticException: / by zero");
        assertThat(evaluation.cases().get(0).verdict()).isEqualTo(CaseVerdict.RUNTIME_ERROR);
    }

    @Test
    void runtimeErrorWithoutMessage_getsAGenericOne() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.RUNTIME_ERROR, " ")),
                List.of(visible(1, "x")));

        assertThat(evaluation.errorMessage()).isEqualTo(AttemptEvaluator.RUNTIME_ERROR_MESSAGE);
    }

    @Test
    void outOfMemory_isARuntimeErrorWithAFixedMessage() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.OUT_OF_MEMORY, "java.lang.OutOfMemoryError: Java heap space")),
                List.of(visible(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.RUNTIME_ERROR);
        assertThat(evaluation.errorMessage()).isEqualTo(AttemptEvaluator.OUT_OF_MEMORY_MESSAGE);
    }

    @Test
    void caseTimeout_statusIsTimeout() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.TIMEOUT, "")),
                List.of(visible(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.TIMEOUT);
        assertThat(evaluation.errorMessage()).isEqualTo(AttemptEvaluator.CASE_TIMEOUT_MESSAGE);
    }

    // ---------- which failure decides the overall status ----------

    @Test
    void firstFailingCaseInOrderDecidesTheStatus() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "1"), ok(2, "wrong"), failing(3, CaseStatus.TIMEOUT, ""), ok(4, "4")),
                List.of(visible(1, "1"), visible(2, "2"), visible(3, "3"), visible(4, "4")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(evaluation.passedCases()).isEqualTo(2);
        assertThat(evaluation.totalCases()).isEqualTo(4);
        assertThat(evaluation.cases()).extracting(CaseOutcome::verdict)
                .containsExactly(CaseVerdict.PASSED, CaseVerdict.FAILED, CaseVerdict.TIMEOUT, CaseVerdict.PASSED);
    }

    @Test
    void timeoutBeforeWrongOutput_statusIsTimeout() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(failing(1, CaseStatus.TIMEOUT, ""), ok(2, "wrong")),
                List.of(visible(1, "1"), visible(2, "2")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.TIMEOUT);
    }

    @Test
    void messageComesFromTheFirstFailure_notFromALaterVisibleOne() {
        AttemptEvaluation evaluation = evaluator.evaluate(
                answer(ok(1, "wrong secret"), failing(2, CaseStatus.RUNTIME_ERROR, "boom")),
                List.of(hidden(1, "x"), visible(2, "y")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(evaluation.errorMessage()).isNull();
        assertThat(evaluation.actualOutput()).isNull();
        assertThat(evaluation.cases().get(1).message()).isEqualTo("boom");
    }

    // ---------- compilation ----------

    @Test
    void compileError_carriesTheCompilerMessageAndNoCases() {
        RunnerResponse response = new RunnerResponse(
                new Compile(false, false, "Solution.java:3: error: ';' expected"), List.of());

        AttemptEvaluation evaluation = evaluator.evaluate(response, List.of(visible(1, "x"), hidden(2, "y")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.COMPILE_ERROR);
        assertThat(evaluation.errorMessage()).isEqualTo("Solution.java:3: error: ';' expected");
        assertThat(evaluation.passedCases()).isZero();
        assertThat(evaluation.totalCases()).isEqualTo(2);
        assertThat(evaluation.cases()).isEmpty();
    }

    @Test
    void compileErrorWithoutMessage_getsAGenericOne() {
        RunnerResponse response = new RunnerResponse(new Compile(false, false, null), List.of());

        AttemptEvaluation evaluation = evaluator.evaluate(response, List.of(visible(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.COMPILE_ERROR);
        assertThat(evaluation.errorMessage()).isNotBlank();
    }

    @Test
    void compileTimeout_statusIsTimeout() {
        RunnerResponse response = new RunnerResponse(new Compile(false, true, "ignored"), List.of());

        AttemptEvaluation evaluation = evaluator.evaluate(response, List.of(visible(1, "x")));

        assertThat(evaluation.status()).isEqualTo(CodingResultStatus.TIMEOUT);
        assertThat(evaluation.errorMessage()).isEqualTo(AttemptEvaluator.COMPILE_TIMEOUT_MESSAGE);
    }

    // ---------- broken answers ----------

    @Test
    void wrongNumberOfResults_isRejected() {
        assertThatThrownBy(() -> evaluator.evaluate(answer(ok(1, "1")), List.of(visible(1, "1"), visible(2, "2"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultWithAnotherNumberThanTheCase_isRejected() {
        assertThatThrownBy(() -> evaluator.evaluate(answer(ok(2, "1")), List.of(visible(1, "1"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- secrets ----------

    @Test
    void expectationToString_doesNotShowTheExpectedOutput() {
        assertThat(visible(1, "top secret answer").toString()).doesNotContain("top secret answer");
    }
}
