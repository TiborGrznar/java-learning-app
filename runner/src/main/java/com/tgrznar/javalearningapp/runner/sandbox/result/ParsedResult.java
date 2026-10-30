package com.tgrznar.javalearningapp.runner.sandbox.result;

import java.util.List;

/**
 * Parsed and sanitized outcome of one submission.
 *
 * @param compile the compilation result
 * @param cases   one entry per case, empty when the compilation failed
 */
public record ParsedResult(CompileResult compile, List<CaseResult> cases) {

    public ParsedResult {
        cases = List.copyOf(cases);
    }
}