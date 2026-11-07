package com.tgrznar.javalearningapp.codingtask.admin;

import com.tgrznar.javalearningapp.coderunner.CodeRunnerBusyException;
import com.tgrznar.javalearningapp.coderunner.CodeRunnerUnavailableException;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseResult;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.Compile;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingtask.dto.ReferenceCheckFailure;
import com.tgrznar.javalearningapp.codingtask.exception.ReferenceSolutionFailedException;
import com.tgrznar.javalearningapp.learning.coding.AttemptEvaluator;
import com.tgrznar.javalearningapp.testsupport.FakeCodeRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The reference solution check with the real evaluator and a scripted runner: what goes to the runner, when the
 * check passes, and what the administrator is told for every way it can fail.
 */
class ReferenceSolutionVerifierTest {

    private static final String SOLUTION = "public class Solution {\n  static int add(int a, int b) { return a + b; }\n}\n";
    private static final String DRIVER = "public class Main { public static void main(String[] a) {} }";

    private FakeCodeRunner runner;
    private ReferenceSolutionVerifier verifier;

    @BeforeEach
    void setUp() {
        runner = new FakeCodeRunner();
        verifier = new ReferenceSolutionVerifier(runner, new AttemptEvaluator());
    }

    // ---------- a passing reference solution ----------

    @Test
    void verify_everyCasePasses_returnsNormally() {
        runner.respondWith(FakeCodeRunner.okWith("3\n", "10\n"));

        assertThatCode(() -> verifier.verify(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"), caseOf(2, "5 5\n", "10\n"))))
                .doesNotThrowAnyException();
    }

    @Test
    void verify_sendsTheSolutionTheDriverAndTheInputsInOrder_neverTheExpectedOutputs() {
        runner.respondWith(FakeCodeRunner.okWith("3\n", "10\n"));

        verifier.verify(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"), caseOf(2, "5 5\n", "10\n")));

        assertThat(runner.calls()).hasSize(1);
        FakeCodeRunner.Call call = runner.calls().get(0);
        assertThat(call.solutionSource()).isEqualTo(SOLUTION);
        assertThat(call.driverSource()).isEqualTo(DRIVER);
        assertThat(call.caseInputs()).containsExactly("1 2\n", "5 5\n");
    }

    @Test
    void verify_taskWithoutDriver_sendsNullDriver() {
        runner.respondWith(FakeCodeRunner.okWith("3\n"));

        verifier.verify(snapshot(null, caseOf(1, "", "3\n")));

        assertThat(runner.calls().get(0).driverSource()).isNull();
    }

    @Test
    void verify_usesTheSameComparisonAsStudents_trailingSpacesAndBlankLinesDoNotMatter() {
        runner.respondWith(FakeCodeRunner.okWith("3   \r\n\r\n"));

        assertThatCode(() -> verifier.verify(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"))))
                .doesNotThrowAnyException();
    }

    // ---------- a failing reference solution ----------

    @Test
    void verify_wrongOutput_reportsFailedCaseWithExpectedAndActualOutput() {
        runner.respondWith(FakeCodeRunner.okWith("3\n", "11\n"));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"), caseOf(2, "5 5\n", "10\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.FAILED);
        assertThat(failure.failedCase()).isEqualTo(2);
        assertThat(failure.passedCases()).isEqualTo(1);
        assertThat(failure.totalCases()).isEqualTo(2);
        assertThat(failure.expectedOutput()).isEqualTo("10\n");
        assertThat(failure.actualOutput()).isEqualTo("11\n");
    }

    @Test
    void verify_hiddenCasesAreDiagnosedLikeVisibleOnes_theAdminSeesTheFullRuntimeMessage() {
        String message = "Exception in thread \"main\" java.lang.IllegalStateException: detail of the hidden input 42\n"
                + "\tat Solution.main(Solution.java:3)";
        runner.respondWith(FakeCodeRunner.compiled(List.of(
                FakeCodeRunner.ok(1, "3\n"),
                FakeCodeRunner.crashed(2, message))));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"), caseOf(2, "42\n", "x\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.RUNTIME_ERROR);
        assertThat(failure.failedCase()).isEqualTo(2);
        assertThat(failure.message()).contains("detail of the hidden input 42").contains("Solution.java:3");
        assertThat(failure.expectedOutput()).isNull();
        assertThat(failure.actualOutput()).isNull();
    }

    @Test
    void verify_compileError_reportsTheCompilerMessageWithoutACase() {
        runner.respondWith(FakeCodeRunner.compileError("Solution.java:2: error: ';' expected"));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.COMPILE_ERROR);
        assertThat(failure.failedCase()).isNull();
        assertThat(failure.passedCases()).isZero();
        assertThat(failure.totalCases()).isEqualTo(1);
        assertThat(failure.message()).contains("';' expected");
    }

    @Test
    void verify_compilationTimeout_reportsATimeoutWithoutACase() {
        runner.respondWith(new RunnerResponse(new Compile(false, true, ""), List.of()));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.TIMEOUT);
        assertThat(failure.failedCase()).isNull();
    }

    @Test
    void verify_caseTimeout_reportsTheCase() {
        runner.respondWith(FakeCodeRunner.compiled(List.of(
                FakeCodeRunner.ok(1, "3\n"),
                new CaseResult(2, CaseStatus.TIMEOUT, 5_000, null, ""))));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n"), caseOf(2, "5 5\n", "10\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.TIMEOUT);
        assertThat(failure.failedCase()).isEqualTo(2);
        assertThat(failure.passedCases()).isEqualTo(1);
    }

    @Test
    void verify_outOfMemory_isReportedAsARuntimeError() {
        runner.respondWith(FakeCodeRunner.compiled(List.of(new CaseResult(1, CaseStatus.OUT_OF_MEMORY, 900, null, ""))));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1 2\n", "3\n")));

        assertThat(failure.status()).isEqualTo(CodingResultStatus.RUNTIME_ERROR);
        assertThat(failure.failedCase()).isEqualTo(1);
    }

    @Test
    void verify_firstFailureWins_laterCasesAreNotReported() {
        runner.respondWith(FakeCodeRunner.okWith("0\n", "0\n", "0\n"));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER,
                caseOf(1, "a\n", "1\n"), caseOf(2, "b\n", "0\n"), caseOf(3, "c\n", "9\n")));

        assertThat(failure.failedCase()).isEqualTo(1);
        assertThat(failure.passedCases()).isEqualTo(1);
    }

    @Test
    void verify_veryLongOutput_isCutSoTheAnswerStaysSmall() {
        String huge = "x".repeat(ReferenceSolutionVerifier.MAX_TEXT_LENGTH * 3);
        runner.respondWith(FakeCodeRunner.okWith(huge));

        ReferenceCheckFailure failure = failureOf(snapshot(DRIVER, caseOf(1, "1\n", huge + "y")));

        assertThat(failure.actualOutput()).hasSize(ReferenceSolutionVerifier.MAX_TEXT_LENGTH + 1).endsWith("…");
        assertThat(failure.expectedOutput()).hasSize(ReferenceSolutionVerifier.MAX_TEXT_LENGTH + 1).endsWith("…");
    }

    @Test
    void failureException_messageDoesNotContainTheOutputs() {
        runner.respondWith(FakeCodeRunner.okWith("SECRET-ACTUAL\n"));

        assertThatThrownBy(() -> verifier.verify(snapshot(DRIVER, caseOf(1, "1\n", "SECRET-EXPECTED\n"))))
                .isInstanceOf(ReferenceSolutionFailedException.class)
                .hasMessageNotContaining("SECRET");
    }

    // ---------- the runner is the problem ----------

    @Test
    void verify_busyRunner_propagatesAndIsNotAVerdict() {
        runner.failWith(new CodeRunnerBusyException(5));

        assertThatThrownBy(() -> verifier.verify(snapshot(DRIVER, caseOf(1, "1\n", "1\n"))))
                .isInstanceOf(CodeRunnerBusyException.class);
    }

    @Test
    void verify_unavailableRunner_propagatesAndIsNotAVerdict() {
        runner.failWith(new CodeRunnerUnavailableException("The code runner answered HTTP 500"));

        assertThatThrownBy(() -> verifier.verify(snapshot(DRIVER, caseOf(1, "1\n", "1\n"))))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    // ---------- helpers ----------

    private ReferenceCheckFailure failureOf(ActivationSnapshot snapshot) {
        try {
            verifier.verify(snapshot);
        } catch (ReferenceSolutionFailedException e) {
            return e.getFailure();
        }
        throw new AssertionError("The verification was expected to fail");
    }

    private static ActivationSnapshot snapshot(String driver, ActivationSnapshot.Case... cases) {
        return new ActivationSnapshot(5L, 11L, SOLUTION, driver, List.of(cases));
    }

    private static ActivationSnapshot.Case caseOf(int number, String input, String expected) {
        return new ActivationSnapshot.Case(number, input, expected);
    }
}
