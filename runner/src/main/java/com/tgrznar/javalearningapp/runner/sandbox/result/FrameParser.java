package com.tgrznar.javalearningapp.runner.sandbox.result;

import com.tgrznar.javalearningapp.runner.sandbox.exception.SandboxUnavailableException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the length-prefixed frames written by launcher.sh:
 * <pre>
 * @@JLA 1
 * @@COMPILE exit timed_out bytes\n body \n
 * @@CASE number exit timed_out millis stdout_bytes stderr_bytes\n stdout stderr \n
 * @@DONE
 * </pre>
 * The input is untrusted (the student's program writes into the bodies). The parser therefore
 * never searches the bodies for text: it reads exactly the number of bytes the header announces,
 * so a program that prints "@@DONE" or a fake header cannot forge a frame. Anything unexpected
 * (a wrong header, a bad length, trailing data, wrong number of cases) is reported as a fault of
 * the sandbox, never as a verdict on the student's code.
 */
public final class FrameParser {

    static final String TOO_MANY_THREADS = "Program vytvoril príliš veľa vlákien.";

    private static final int MAX_HEADER_BYTES = 100;
    private static final Pattern COMPILE = Pattern.compile("@@COMPILE (\\d{1,3}) ([01]) (\\d{1,9})");
    private static final Pattern CASE =
            Pattern.compile("@@CASE (\\d{1,9}) (\\d{1,3}) ([01]) (\\d{1,9}) (\\d{1,9}) (\\d{1,9})");

    private final byte[] data;
    private final int maxFieldBytes;
    private int pos;

    private FrameParser(byte[] data, int maxFieldBytes) {
        this.data = data;
        this.maxFieldBytes = maxFieldBytes;
    }

    /**
     * @param data          the launcher's stdout
     * @param expectedCases number of cases sent in the request (0 means one run with empty stdin)
     * @param maxFieldBytes upper limit of one body (the launcher's output cap)
     * @throws SandboxUnavailableException when the frames are malformed or do not match the request
     */
    public static ParsedResult parse(byte[] data, int expectedCases, int maxFieldBytes) {
        return new FrameParser(data, maxFieldBytes).parseAll(Math.max(1, expectedCases));
    }

    private ParsedResult parseAll(int expectedRuns) {
        if (!"@@JLA 1".equals(readLine())) {
            throw fail("unknown protocol header");
        }

        Matcher compileHeader = COMPILE.matcher(readLine());
        if (!compileHeader.matches()) {
            throw fail("bad compile header");
        }
        int compileExit = Integer.parseInt(compileHeader.group(1));
        boolean compileTimedOut = "1".equals(compileHeader.group(2));
        String compileOutput = utf8(readBytes(Integer.parseInt(compileHeader.group(3))));
        expectNewline();
        boolean success = compileExit == 0 && !compileTimedOut;
        CompileResult compile = new CompileResult(success, compileTimedOut,
                success ? "" : OutputSanitizer.message(compileOutput, OutputSanitizer.MESSAGE_LIMIT));

        List<CaseResult> cases = new ArrayList<>();
        String line = readLine();
        while (!"@@DONE".equals(line)) {
            if (!success) {
                throw fail("case frame after a failed compilation");
            }
            if (cases.size() >= expectedRuns) {
                throw fail("more case frames than cases");
            }
            Matcher header = CASE.matcher(line);
            if (!header.matches()) {
                throw fail("bad frame header");
            }
            int number = Integer.parseInt(header.group(1));
            if (number != cases.size() + 1) {
                throw fail("unexpected case number");
            }
            int exit = Integer.parseInt(header.group(2));
            boolean timedOut = "1".equals(header.group(3));
            long millis = Long.parseLong(header.group(4));
            byte[] stdout = readBytes(Integer.parseInt(header.group(5)));
            byte[] stderr = readBytes(Integer.parseInt(header.group(6)));
            expectNewline();
            cases.add(classify(number, exit, timedOut, millis, stdout, stderr));
            line = readLine();
        }

        if (pos != data.length) {
            throw fail("data after the closing frame");
        }
        if (success && cases.size() != expectedRuns) {
            throw fail("missing case frames");
        }
        return new ParsedResult(compile, cases);
    }

    private static CaseResult classify(int number, int exit, boolean timedOut, long millis,
                                       byte[] stdout, byte[] stderr) {
        String err = utf8(stderr);
        if (timedOut) {
            String message = err.contains("unable to create native thread") ? TOO_MANY_THREADS : "";
            return new CaseResult(number, CaseStatus.TIMEOUT, millis, "", message);
        }
        if (exit == 3 && err.contains("OutOfMemoryError")) {
            return new CaseResult(number, CaseStatus.OUT_OF_MEMORY, millis, "", "");
        }
        if (exit == 0) {
            return new CaseResult(number, CaseStatus.OK, millis, utf8(stdout), "");
        }
        return new CaseResult(number, CaseStatus.RUNTIME_ERROR, millis, "", OutputSanitizer.runtimeError(err));
    }

    /** Reads one header line (without the newline); headers are short, so the search is bounded. */
    private String readLine() {
        int limit = Math.min(data.length, pos + MAX_HEADER_BYTES);
        for (int i = pos; i < limit; i++) {
            if (data[i] == '\n') {
                String line = new String(data, pos, i - pos, StandardCharsets.US_ASCII);
                pos = i + 1;
                return line;
            }
        }
        throw fail("header line missing or too long");
    }

    private byte[] readBytes(int length) {
        if (length > maxFieldBytes || length > data.length - pos) {
            throw fail("announced length is out of range");
        }
        byte[] bytes = Arrays.copyOfRange(data, pos, pos + length);
        pos += length;
        return bytes;
    }

    private void expectNewline() {
        if (pos >= data.length || data[pos] != '\n') {
            throw fail("frame body is not followed by a newline");
        }
        pos++;
    }

    private static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static SandboxUnavailableException fail(String reason) {
        return new SandboxUnavailableException("Malformed result frames: " + reason);
    }
}