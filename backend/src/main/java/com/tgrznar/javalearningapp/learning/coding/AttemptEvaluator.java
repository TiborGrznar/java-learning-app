package com.tgrznar.javalearningapp.learning.coding;

import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the answer of the code runner into a verdict. The comparison with the expected output happens
 * here, in the backend: the runner never learns what the expected output is.
 * <p>
 * Overall status: PASSED when every case passes; COMPILE_ERROR when the code did not compile (TIMEOUT
 * when the compilation itself timed out); otherwise the status of the first failing case in the order
 * of the cases (TIMEOUT, RUNTIME_ERROR or FAILED). Running out of memory counts as a runtime error.
 * <p>
 * What the student may see is decided here. A visible case shows the message of the runner and, when
 * the output was wrong, what the program printed. A hidden case never shows its output. After a crash
 * it shows only a fixed text (timeout, out of memory) or a safe summary of the exception: the type, taken
 * from a short list of well-known JDK exceptions, and the line in the student's own file. The text of the
 * exception is never shown, because the program reads the hidden input and could print it into the
 * exception. The student controls everything the program writes, so anything taken from it can carry a few
 * bits of information; this is limited to a choice from the list and a line inside the student's own file,
 * which is no more than the verdict itself tells.
 */
@Component
public class AttemptEvaluator {

    static final String COMPILE_TIMEOUT_MESSAGE = "Kompilácia trvala príliš dlho";
    static final String CASE_TIMEOUT_MESSAGE = "Program bežal príliš dlho";
    static final String OUT_OF_MEMORY_MESSAGE = "Program vyčerpal pamäť, ktorú mal k dispozícii";
    static final String RUNTIME_ERROR_MESSAGE = "Program skončil chybou";

    private static final Set<String> KNOWN_EXCEPTIONS = Set.of(
            "java.lang.ArithmeticException",
            "java.lang.ArrayIndexOutOfBoundsException",
            "java.lang.ArrayStoreException",
            "java.lang.ClassCastException",
            "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException",
            "java.lang.IndexOutOfBoundsException",
            "java.lang.NegativeArraySizeException",
            "java.lang.NullPointerException",
            "java.lang.NumberFormatException",
            "java.lang.StackOverflowError",
            "java.lang.StringIndexOutOfBoundsException",
            "java.lang.UnsupportedOperationException",
            "java.util.ConcurrentModificationException",
            "java.util.EmptyStackException",
            "java.util.InputMismatchException",
            "java.util.NoSuchElementException");

    private static final Pattern EXCEPTION_LINE = Pattern.compile(
            "(?m)^Exception in thread \"[^\"\\r\\n]{1,64}\" (java\\.(?:lang|util)\\.[A-Z][A-Za-z]{0,60})(?=[:\\s]|$)");

    private static final Pattern STUDENT_FRAME = Pattern.compile(
            "(?m)^\\s*at Solution\\.[\\w$<>]+\\(Solution\\.java:(\\d{1,5})\\)");

    /**
     * @param response          the answer of the runner, already verified by the runner client
     * @param expected          the cases of the task, in order, numbered 1..n
     * @param solutionLineCount number of lines of the submitted code, a line number outside of it is never shown
     * @throws IllegalArgumentException when a successful compilation does not come with exactly one result per case
     */
    public AttemptEvaluation evaluate(RunnerResponse response, List<CaseExpectation> expected, int solutionLineCount) {
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
            CaseOutcome outcome = outcomeOf(expectation, result, solutionLineCount);
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

    private static CaseOutcome outcomeOf(CaseExpectation expectation, RunnerResponse.CaseResult result, int solutionLineCount) {
        boolean hidden = expectation.hidden();
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
                    actualOutput = hidden ? null : stdout;
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
                message = hidden
                        ? safeSummary(result.message(), solutionLineCount)
                        : (result.message() == null || result.message().isBlank() ? RUNTIME_ERROR_MESSAGE : result.message());
            }
            default -> throw new IllegalArgumentException("Unknown case status " + result.status());
        }
        return new CaseOutcome(expectation.number(), hidden, verdict, message, actualOutput);
    }

    /**
     * The exception type (only a well-known JDK one) and the line in the student's own file, never the text
     * of the exception. Anything that does not look exactly right gives the generic message.
     */
    private static String safeSummary(String runnerMessage, int solutionLineCount) {
        if (runnerMessage == null) {
            return RUNTIME_ERROR_MESSAGE;
        }
        Matcher type = EXCEPTION_LINE.matcher(runnerMessage);
        if (!type.find() || !KNOWN_EXCEPTIONS.contains(type.group(1))) {
            return RUNTIME_ERROR_MESSAGE;
        }
        String name = type.group(1).substring(type.group(1).lastIndexOf('.') + 1);
        Matcher frame = STUDENT_FRAME.matcher(runnerMessage);
        if (frame.find()) {
            int line = Integer.parseInt(frame.group(1));
            if (line >= 1 && line <= solutionLineCount) {
                return "Výnimka " + name + " (Solution.java, riadok " + line + ")";
            }
        }
        return "Výnimka " + name;
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