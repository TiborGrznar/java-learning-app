package com.tgrznar.javalearningapp.runner.sandbox.result;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputSanitizerTest {

    @Test
    void message_removesJvmLogLines() {
        String raw = "[0.012s][warning][os,thread] Failed to start thread\nreal text\n[1.5s][info][gc] x";

        assertThat(OutputSanitizer.message(raw, 500)).isEqualTo("real text");
    }

    @Test
    void message_stripsInternalPaths() {
        String raw = "/work/src/Solution.java:3: error: ';' expected";

        assertThat(OutputSanitizer.message(raw, 500)).isEqualTo("Solution.java:3: error: ';' expected");
    }

    @Test
    void message_removesControlCharactersButKeepsNewlineAndTab() {
        String raw = "a\u0000b\u001b[31mc\r\nd\te\u202Ef";

        assertThat(OutputSanitizer.message(raw, 500)).isEqualTo("ab[31mc\nd\tef");
    }

    @Test
    void message_cutsLongTextWithAnEllipsis() {
        String result = OutputSanitizer.message("a".repeat(600), 500);

        assertThat(result).hasSize(501).endsWith("…");
    }

    @Test
    void message_doesNotSplitSurrogatePairs() {
        String raw = "😀".repeat(10);

        String result = OutputSanitizer.message(raw, 3);

        assertThat(result).isEqualTo("😀😀😀…");
    }

    @Test
    void runtimeError_keepsExceptionAndStudentFramesOnly() {
        String stderr = """
                Exception in thread "main" java.lang.ArithmeticException: / by zero
                \tat Solution.divide(Solution.java:3)
                \tat Main.main(Main.java:7)
                \tat java.base/java.lang.Thread.run(Thread.java:1583)
                \t... 2 more
                """;

        String result = OutputSanitizer.runtimeError(stderr);

        assertThat(result).contains("java.lang.ArithmeticException: / by zero");
        assertThat(result).contains("at Solution.divide(Solution.java:3)");
        assertThat(result).doesNotContain("Main").doesNotContain("java.base").doesNotContain("more");
    }

    @Test
    void runtimeError_limitsTheNumberOfStackFrames() {
        StringBuilder stderr = new StringBuilder("Exception in thread \"main\" java.lang.StackOverflowError\n");
        for (int i = 0; i < 1000; i++) {
            stderr.append("\tat Solution.f(Solution.java:3)\n");
        }

        String result = OutputSanitizer.runtimeError(stderr.toString());

        assertThat(result.lines().filter(l -> l.contains("at Solution.f")).count()).isLessThanOrEqualTo(8);
        assertThat(result.length()).isLessThanOrEqualTo(501);
    }
}