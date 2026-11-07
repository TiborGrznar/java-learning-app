package com.tgrznar.javalearningapp.testsupport;

import com.tgrznar.javalearningapp.coderunner.CodeRunner;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseResult;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.CaseStatus;
import com.tgrznar.javalearningapp.coderunner.RunnerResponse.Compile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A code runner for tests: it records every call and answers what the test scripted, nothing is executed.
 * It lives in the test sources only, so it can never end up in the production JAR. Without a script it
 * fails loudly, so a test never passes by accident on an unexpected call.
 */
public class FakeCodeRunner implements CodeRunner {

    /** One call the application made. */
    public record Call(String solutionSource, String driverSource, List<String> caseInputs) {
    }

    private final List<Call> calls = new CopyOnWriteArrayList<>();
    private volatile Function<Call, RunnerResponse> behaviour = FakeCodeRunner::unscripted;

    @Override
    public RunnerResponse run(String solutionSource, String driverSource, List<String> caseInputs) {
        Call call = new Call(solutionSource, driverSource, List.copyOf(caseInputs));
        calls.add(call);
        return behaviour.apply(call);
    }

    /** Forgets the calls and the script. Call it before every test, the bean is shared by the whole context. */
    public void reset() {
        calls.clear();
        behaviour = FakeCodeRunner::unscripted;
    }

    public void respondWith(RunnerResponse response) {
        behaviour = call -> response;
    }

    public void respondWith(Function<Call, RunnerResponse> script) {
        behaviour = script;
    }

    public void failWith(RuntimeException error) {
        behaviour = call -> {
            throw error;
        };
    }

    public List<Call> calls() {
        return List.copyOf(calls);
    }

    private static RunnerResponse unscripted(Call call) {
        throw new IllegalStateException("FakeCodeRunner was called but no response was scripted");
    }

    // ---------- builders of answers ----------

    /** The code compiled and every case ended normally with the given standard output, numbered from 1. */
    public static RunnerResponse okWith(String... outputs) {
        List<CaseResult> cases = new ArrayList<>();
        for (int i = 0; i < outputs.length; i++) {
            cases.add(new CaseResult(i + 1, CaseStatus.OK, 5, outputs[i], ""));
        }
        return compiled(cases);
    }

    public static RunnerResponse compiled(List<CaseResult> cases) {
        return new RunnerResponse(new Compile(true, false, ""), List.copyOf(cases));
    }

    public static RunnerResponse compileError(String message) {
        return new RunnerResponse(new Compile(false, false, message), List.of());
    }

    public static CaseResult ok(int number, String stdout) {
        return new CaseResult(number, CaseStatus.OK, 5, stdout, "");
    }

    public static CaseResult crashed(int number, String stderr) {
        return new CaseResult(number, CaseStatus.RUNTIME_ERROR, 5, null, stderr);
    }
}
