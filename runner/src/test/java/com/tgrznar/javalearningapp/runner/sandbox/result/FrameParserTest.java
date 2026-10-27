package com.tgrznar.javalearningapp.runner.sandbox.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FrameParserTest {

    private static final int MAX = 65536;

    private static int len(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String compile(int exit, int timedOut, String output) {
        return "@@COMPILE " + exit + " " + timedOut + " " + len(output) + "\n" + output + "\n";
    }

    private static String caze(int number, int exit, int timedOut, int millis, String out, String err) {
        return "@@CASE " + number + " " + exit + " " + timedOut + " " + millis + " "
                + len(out) + " " + len(err) + "\n" + out + err + "\n";
    }

    private static String frames(String... parts) {
        return "@@JLA 1\n" + String.join("", parts) + "@@DONE\n";
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static ParsedResult parse(String text, int expectedCases) {
        return FrameParser.parse(bytes(text), expectedCases, MAX);
    }

    @Test
    void okCase_keepsStdoutAndDropsStderr() {
        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 0, 0, 12, "9\n", "noise\n")), 1);

        assertThat(result.compile().success()).isTrue();
        assertThat(result.cases()).hasSize(1);
        CaseResult c = result.cases().get(0);
        assertThat(c.number()).isEqualTo(1);
        assertThat(c.status()).isEqualTo(CaseStatus.OK);
        assertThat(c.millis()).isEqualTo(12);
        assertThat(c.stdout()).isEqualTo("9\n");
        assertThat(c.message()).isEmpty();
    }

    @Test
    void severalCases_areReturnedInOrder() {
        ParsedResult result = parse(frames(compile(0, 0, ""),
                caze(1, 0, 0, 5, "9\n", ""), caze(2, 0, 0, 6, "25\n", ""), caze(3, 0, 0, 7, "144\n", "")), 3);

        assertThat(result.cases()).extracting(CaseResult::stdout).containsExactly("9\n", "25\n", "144\n");
        assertThat(result.cases()).extracting(CaseResult::number).containsExactly(1, 2, 3);
    }

    @Test
    void programOutputThatLooksLikeFrames_staysInsideItsBody() {
        String forged = "1\n@@DONE\n@@CASE 2 0 0 1 1 0\nx\n";

        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 0, 0, 5, forged, "")), 1);

        assertThat(result.cases()).hasSize(1);
        assertThat(result.cases().get(0).stdout()).isEqualTo(forged);
    }

    @Test
    void utf8Output_isDecoded() {
        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 0, 0, 5, "ľščťž\n", "")), 1);

        assertThat(result.cases().get(0).stdout()).isEqualTo("ľščťž\n");
    }

    @Test
    void runtimeError_isClassifiedWithASanitizedMessageAndNoStdout() {
        String err = "Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n"
                + "\tat Solution.f(Solution.java:3)\n\tat Main.main(Main.java:5)\n";

        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 1, 0, 5, "partial", err)), 1);

        CaseResult c = result.cases().get(0);
        assertThat(c.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        assertThat(c.stdout()).isEmpty();
        assertThat(c.message()).contains("ArithmeticException").contains("Solution.f").doesNotContain("Main.main");
    }

    @Test
    void timeout_isClassified() {
        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 137, 1, 5000, "", "")), 1);

        CaseResult c = result.cases().get(0);
        assertThat(c.status()).isEqualTo(CaseStatus.TIMEOUT);
        assertThat(c.message()).isEmpty();
    }

    @Test
    void timeoutWithThreadExhaustion_getsTheThreadMessage() {
        String err = "[0.5s][warning][os,thread] Failed to start thread\n"
                + "java.lang.OutOfMemoryError: unable to create native thread\n";

        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 137, 1, 5000, "", err)), 1);

        CaseResult c = result.cases().get(0);
        assertThat(c.status()).isEqualTo(CaseStatus.TIMEOUT);
        assertThat(c.message()).isEqualTo(FrameParser.TOO_MANY_THREADS);
    }

    @Test
    void outOfMemory_isClassified() {
        String err = "Terminating due to java.lang.OutOfMemoryError: Java heap space\n";

        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 3, 0, 300, "", err)), 1);

        assertThat(result.cases().get(0).status()).isEqualTo(CaseStatus.OUT_OF_MEMORY);
    }

    @Test
    void exitCode3WithoutOutOfMemoryText_isAnOrdinaryRuntimeError() {
        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 3, 0, 5, "", "System.exit(3)\n")), 1);

        assertThat(result.cases().get(0).status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
    }

    @Test
    void compileError_hasNoCasesAndASanitizedMessage() {
        String out = "/work/src/Solution.java:3: error: ';' expected\n1 error\n";

        ParsedResult result = parse(frames(compile(1, 0, out)), 2);

        assertThat(result.compile().success()).isFalse();
        assertThat(result.compile().timedOut()).isFalse();
        assertThat(result.compile().message()).startsWith("Solution.java:3: error: ';' expected");
        assertThat(result.cases()).isEmpty();
    }

    @Test
    void compileTimeout_isReported() {
        ParsedResult result = parse(frames(compile(137, 1, "")), 1);

        assertThat(result.compile().success()).isFalse();
        assertThat(result.compile().timedOut()).isTrue();
    }

    @Test
    void zeroExpectedCases_meansOneRun() {
        ParsedResult result = parse(frames(compile(0, 0, ""), caze(1, 0, 0, 5, "hi\n", "")), 0);

        assertThat(result.cases()).hasSize(1);
    }

    static Stream<Arguments> malformed() {
        String okCompile = compile(0, 0, "");
        String okCase = caze(1, 0, 0, 5, "1\n", "");
        return Stream.of(
                Arguments.of("empty input", "", 1, MAX),
                Arguments.of("unknown protocol version", "@@JLA 2\n" + okCompile + okCase + "@@DONE\n", 1, MAX),
                Arguments.of("no compile frame", "@@JLA 1\n@@DONE\n", 1, MAX),
                Arguments.of("no closing frame", "@@JLA 1\n" + okCompile + okCase, 1, MAX),
                Arguments.of("data after the closing frame", frames(okCompile, okCase) + "x", 1, MAX),
                Arguments.of("length beyond the data",
                        "@@JLA 1\n" + okCompile + "@@CASE 1 0 0 1 999 0\nabc\n@@DONE\n", 1, MAX),
                Arguments.of("negative length",
                        "@@JLA 1\n" + okCompile + "@@CASE 1 0 0 1 -1 0\nabc\n@@DONE\n", 1, MAX),
                Arguments.of("body not followed by a newline",
                        "@@JLA 1\n" + okCompile + "@@CASE 1 0 0 1 1 0\nab\n@@DONE\n", 1, MAX),
                Arguments.of("wrong case number", frames(okCompile, caze(2, 0, 0, 5, "1\n", "")), 1, MAX),
                Arguments.of("more case frames than cases",
                        frames(okCompile, okCase, caze(2, 0, 0, 5, "1\n", "")), 1, MAX),
                Arguments.of("missing case frames", frames(okCompile, okCase), 2, MAX),
                Arguments.of("case frame after failed compilation",
                        frames(compile(1, 0, "e"), okCase), 1, MAX),
                Arguments.of("header too long", "@@JLA 1\n" + "x".repeat(200) + "\n", 1, MAX),
                Arguments.of("unknown frame", "@@JLA 1\n" + okCompile + "@@WHAT 1\n@@DONE\n", 1, MAX),
                Arguments.of("field over the limit",
                        frames(okCompile, caze(1, 0, 0, 5, "x".repeat(11), "")), 1, 10));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformed")
    void malformedFrames_areAnInfrastructureFault(String name, String text, int expectedCases, int maxField) {
        assertThatThrownBy(() -> FrameParser.parse(bytes(text), expectedCases, maxField))
                .isInstanceOf(SandboxUnavailableException.class)
                .hasMessageStartingWith("Malformed result frames");
    }
}