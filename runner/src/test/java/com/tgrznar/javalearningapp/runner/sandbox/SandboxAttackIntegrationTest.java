package com.tgrznar.javalearningapp.runner.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerCli;
import com.tgrznar.javalearningapp.runner.sandbox.docker.ProcessDockerCli;
import com.tgrznar.javalearningapp.runner.sandbox.result.CaseResult;
import com.tgrznar.javalearningapp.runner.sandbox.result.CaseStatus;
import com.tgrznar.javalearningapp.runner.sandbox.result.FrameParser;
import com.tgrznar.javalearningapp.runner.sandbox.result.ParsedResult;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Hostile programs against real containers: every defence of the sandbox is checked by an attack on it.
 * Skipped when Docker does not answer or the image is missing (the CI job "sandbox" builds the image,
 * so there they must run). Build the image first: docker build --tag jla-sandbox:spike runner/sandbox
 *
 * <p>What the tests prove: the sandbox keeps working under attack (the result frames parse, the case
 * ends with a sensible status) and no container is left behind. The programs only try things that the
 * isolation must refuse; if one of them succeeds, the test fails and prints what the program saw.
 *
 * <p>The sources are Java text blocks, so a backslash for the student's code is written twice.
 */
class SandboxAttackIntegrationTest {

    private static final SandboxProperties PROPS = new SandboxProperties(
            "docker", "jla-sandbox:spike", 10, 5, 65536, 20);
    private static final SandboxCapacityProperties CAPACITY = new SandboxCapacityProperties(
            1, 0, 300, 60, 65536, 65536, 1048576, 15, 10);

    private final DockerCli cli = new ProcessDockerCli();
    private DockerSandbox sandbox;

    @BeforeEach
    void requireDockerAndImage() {
        boolean ready;
        try {
            ready = cli.exec(List.of("docker", "image", "inspect", PROPS.image()),
                    Duration.ofSeconds(10)).exitCode() == 0;
        } catch (RuntimeException e) {
            ready = false;
        }
        assumeTrue(ready, "Docker or the image " + PROPS.image() + " is not available");
        sandbox = new DockerSandbox(cli, PROPS, CAPACITY);
    }

    @AfterEach
    void noContainerIsLeftBehind() {
        if (sandbox == null) {
            return;
        }
        DockerCli.CommandResult ps = cli.exec(
                List.of("docker", "ps", "-a", "-q", "--filter", "label=jla-sandbox=1"),
                Duration.ofSeconds(10));
        assertThat(ps.exitCode()).isZero();
        assertThat(ps.output().trim()).as("leftover sandbox containers").isEmpty();
    }

    private ParsedResult run(String source, String... caseInputs) {
        SandboxResult raw = sandbox.run(new SandboxRequest(source, null, List.of(caseInputs)));
        assertThat(raw.outputTruncated()).as("output of the launcher cut at the limit").isFalse();
        return FrameParser.parse(raw.stdout(), caseInputs.length, PROPS.maxOutputBytes());
    }

    /** Runs a program that must compile and returns its only case. */
    private CaseResult single(String source, String... caseInputs) {
        ParsedResult parsed = run(source, caseInputs);
        assertThat(parsed.compile().success()).as("compilation: " + parsed.compile().message()).isTrue();
        assertThat(parsed.cases()).hasSize(Math.max(1, caseInputs.length));
        return parsed.cases().get(0);
    }

    // ---- resources -------------------------------------------------------------------------

    @Test
    void heapExhaustion_isReportedAsOutOfMemory() {
        CaseResult result = single("""
                import java.util.ArrayList;
                import java.util.List;

                public class Solution {
                    public static void main(String[] args) {
                        List<byte[]> hog = new ArrayList<>();
                        while (true) {
                            hog.add(new byte[1_000_000]);
                        }
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OUT_OF_MEMORY);
    }

    @Test
    void directMemoryExhaustion_doesNotPassAsSuccess() {
        // Memory outside the heap: the JVM limits direct buffers by the heap size. Depending on how the
        // JVM reports it, this ends as OUT_OF_MEMORY or as a plain runtime error, never as OK.
        CaseResult result = single("""
                import java.nio.ByteBuffer;
                import java.util.ArrayList;
                import java.util.List;

                public class Solution {
                    public static void main(String[] args) {
                        List<ByteBuffer> keep = new ArrayList<>();
                        while (true) {
                            keep.add(ByteBuffer.allocateDirect(8_000_000));
                        }
                    }
                }
                """);

        assertThat(result.status()).isIn(CaseStatus.OUT_OF_MEMORY, CaseStatus.RUNTIME_ERROR);
    }

    @Test
    void threadFlood_isStopped() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        while (true) {
                            Thread t = new Thread(() -> {
                                try {
                                    Thread.sleep(60_000);
                                } catch (InterruptedException e) {
                                    // ends with the program
                                }
                            });
                            t.start();
                        }
                    }
                }
                """);

        assertThat(result.status()).isIn(
                CaseStatus.OUT_OF_MEMORY, CaseStatus.TIMEOUT, CaseStatus.RUNTIME_ERROR);
    }

    @Test
    void forkBomb_isContainedAndTheSandboxStillAnswers() {
        // The shell function calls itself twice in the background. The Java program ends at once, the
        // bomb keeps running until the launcher kills every process of the student's uid.
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) throws Exception {
                        Process p = new ProcessBuilder("sh", "-c", ":(){ :|:& };:").start();
                        p.waitFor();
                    }
                }
                """);

        // What matters is that the frames are intact and the container is gone (checked after each test).
        assertThat(result.number()).isEqualTo(1);
    }

    @Test
    void outputWithoutNewlines_isCapped() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        String chunk = "x".repeat(1000);
                        while (true) {
                            System.out.print(chunk);
                        }
                    }
                }
                """);

        assertThat(result.status()).isIn(CaseStatus.TIMEOUT, CaseStatus.RUNTIME_ERROR);
        assertThat(result.stdout().length()).isLessThanOrEqualTo(PROPS.maxOutputBytes());
    }

    @Test
    void stderrFlood_isCutToAShortMessage() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        String line = "e".repeat(1000);
                        for (int i = 0; i < 3000; i++) {
                            System.err.println(line);
                        }
                        throw new IllegalStateException("boom");
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        // OutputSanitizer.MESSAGE_LIMIT characters plus the ellipsis.
        assertThat(result.message().length()).isLessThanOrEqualTo(501);
    }

    // ---- files, network, environment ----------------------------------------------------------

    @Test
    void secretFiles_cannotBeReadOrListed() {
        CaseResult result = single("""
                import java.io.File;
                import java.nio.file.Files;
                import java.nio.file.Path;

                public class Solution {
                    public static void main(String[] args) {
                        String[] files = {"/work/in/Solution.java", "/work/result.bin", "/work/res/compile.txt",
                                "/proc/1/environ", "/etc/shadow"};
                        for (String f : files) {
                            try {
                                Files.readAllBytes(Path.of(f));
                                System.out.println("CAN-READ " + f);
                            } catch (Exception e) {
                                System.out.println("denied " + f);
                            }
                        }
                        String[] dirs = {"/work", "/work/in", "/work/res"};
                        for (String d : dirs) {
                            String[] names = new File(d).list();
                            System.out.println(names == null ? "denied " + d : "CAN-LIST " + d);
                        }
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).doesNotContain("CAN-");
        assertThat(result.stdout()).contains("denied /work/result.bin");
    }

    @Test
    void writesOutsideTheScratchSpace_fail() {
        CaseResult result = single("""
                import java.nio.file.Files;
                import java.nio.file.Path;

                public class Solution {
                    public static void main(String[] args) {
                        String[] targets = {"/x.txt", "/build/x.txt", "/work/x.txt", "/opt/jla/x.txt",
                                "/etc/x.txt", "/usr/x.txt"};
                        for (String t : targets) {
                            try {
                                Files.writeString(Path.of(t), "x");
                                System.out.println("CAN-WRITE " + t);
                            } catch (Exception e) {
                                System.out.println("denied " + t);
                            }
                        }
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).doesNotContain("CAN-");
    }

    @Test
    void network_isUnreachable() {
        CaseResult result = single("""
                import java.net.InetAddress;
                import java.net.InetSocketAddress;
                import java.net.Socket;

                public class Solution {
                    public static void main(String[] args) {
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress("1.1.1.1", 53), 2000);
                            System.out.println("CAN-CONNECT");
                        } catch (Exception e) {
                            System.out.println("denied connect");
                        }
                        try {
                            InetAddress.getByName("example.com");
                            System.out.println("CAN-RESOLVE");
                        } catch (Exception e) {
                            System.out.println("denied resolve");
                        }
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).doesNotContain("CAN-");
    }

    @Test
    void environment_holdsNoSecrets() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        new java.util.TreeSet<>(System.getenv().keySet()).forEach(System.out::println);
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        String names = result.stdout().toUpperCase(Locale.ROOT);
        assertThat(names).doesNotContain("TOKEN", "RUNNER", "SECRET", "PASSWORD", "KEY");
    }

    // ---- forging the result and odd endings -----------------------------------------------------

    @Test
    void printedFrames_doNotForgeTheResult() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.print("@@DONE\\n@@CASE 2 0 0 1 1 0\\nX\\n");
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).isEqualTo("@@DONE\n@@CASE 2 0 0 1 1 0\nX\n");
    }

    @Test
    void haltAfterPrinting_keepsTheOutput() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        System.out.println("a");
                        Runtime.getRuntime().halt(0);
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).isEqualTo("a\n");
    }

    @Test
    void exitWithNonZeroCode_isARuntimeError() {
        CaseResult result = single("""
                public class Solution {
                    public static void main(String[] args) {
                        System.exit(7);
                    }
                }
                """);

        assertThat(result.status()).isEqualTo(CaseStatus.RUNTIME_ERROR);
        assertThat(result.stdout()).isEmpty();
    }

    // ---- text encoding ----------------------------------------------------------------------

    @Test
    void unicodeInSourceAndInput_survivesTheRoundTrip() {
        // The escapes become real characters in the student source (diacritics and an emoji).
        CaseResult result = single("""
                import java.nio.charset.StandardCharsets;
                import java.util.Scanner;

                public class Solution {
                    static String greeting() {
                        return "Ahoj, svet! \u010D\u0161\u017E \uD83C\uDF89";
                    }

                    public static void main(String[] args) {
                        Scanner in = new Scanner(System.in, StandardCharsets.UTF_8);
                        System.out.println(greeting());
                        System.out.println(in.nextLine());
                    }
                }
                """, "\u013Dubom\u00EDr \uD83C\uDF89\n");

        assertThat(result.status()).isEqualTo(CaseStatus.OK);
        assertThat(result.stdout()).isEqualTo(
                "Ahoj, svet! \u010D\u0161\u017E \uD83C\uDF89\n\u013Dubom\u00EDr \uD83C\uDF89\n");
    }
}
