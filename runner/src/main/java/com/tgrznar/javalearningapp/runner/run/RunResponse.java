package com.tgrznar.javalearningapp.runner.run;

import com.tgrznar.javalearningapp.runner.sandbox.result.CaseResult;
import com.tgrznar.javalearningapp.runner.sandbox.result.CompileResult;
import com.tgrznar.javalearningapp.runner.sandbox.result.ParsedResult;
import java.util.List;

/**
 * Body of a successful POST /run. Everything in it is already sanitized for the student.
 *
 * @param compile the compilation result
 * @param cases   one entry per case in request order, empty when the compilation failed
 */
public record RunResponse(CompileResult compile, List<CaseResult> cases) {

    public RunResponse {
        cases = List.copyOf(cases);
    }

    static RunResponse from(ParsedResult parsed) {
        return new RunResponse(parsed.compile(), parsed.cases());
    }
}
