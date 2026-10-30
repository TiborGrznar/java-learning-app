package com.tgrznar.javalearningapp.runner.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxCapacityProperties;
import com.tgrznar.javalearningapp.runner.sandbox.config.SandboxProperties;
import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerCli;
import com.tgrznar.javalearningapp.runner.sandbox.docker.ProcessDockerCli;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Runs real containers. Skipped when Docker does not answer or the image is missing.
 * Build the image first: docker build --tag jla-sandbox:spike runner/sandbox
 */
class DockerSandboxIntegrationTest {

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

    private static String text(SandboxResult result) {
        return new String(result.stdout(), StandardCharsets.UTF_8);
    }

    private void assertNoLeftoverContainer() {
        DockerCli.CommandResult ps = cli.exec(
                List.of("docker", "ps", "-a", "-q", "--filter", "label=jla-sandbox=1"),
                Duration.ofSeconds(10));
        assertThat(ps.exitCode()).isZero();
        assertThat(ps.output().trim()).isEmpty();
    }

    private static final String SOLUTION = """
            public class Solution {
                static int square(int n) {
                    return n * n;
                }
            }
            """;

    private static final String DRIVER = """
            import java.util.Scanner;

            public class Main {
                public static void main(String[] args) {
                    Scanner in = new Scanner(System.in);
                    int n = in.nextInt();
                    System.out.println(Solution.square(n));
                }
            }
            """;

    @Test
    void okDriver_streamsFramesForEveryCase() {
        SandboxResult result = sandbox.run(new SandboxRequest(SOLUTION, DRIVER, List.of("3\n", "5\n", "12\n")));

        String out = text(result);
        assertThat(result.exitCode()).isZero();
        assertThat(result.outputTruncated()).isFalse();
        assertThat(out).startsWith("@@JLA 1\n").endsWith("@@DONE\n");
        assertThat(out).containsPattern("@@COMPILE 0 0 \\d+\n");
        assertThat(out).containsPattern("@@CASE 1 0 0 \\d+ 2 \\d+\n9\n");
        assertThat(out).containsPattern("@@CASE 2 0 0 \\d+ 3 \\d+\n25\n");
        assertThat(out).containsPattern("@@CASE 3 0 0 \\d+ 4 \\d+\n144\n");
        assertNoLeftoverContainer();
    }

    @Test
    void compileError_givesCompileFrameAndNoCaseFrame() {
        String broken = """
                public class Solution {
                    public static void main(String[] args) {
                        int x = "text"
                        System.out.println(x);
                    }
                }
                """;

        SandboxResult result = sandbox.run(new SandboxRequest(broken, null, List.of()));

        String out = text(result);
        assertThat(out).doesNotContainPattern("@@COMPILE 0 ");
        assertThat(out).containsPattern("@@COMPILE [1-9]\\d* 0 \\d+\n");
        assertThat(out).doesNotContain("@@CASE");
        assertThat(out).endsWith("@@DONE\n");
        assertNoLeftoverContainer();
    }

    @Test
    void infiniteLoop_isReportedAsTimedOutAndLeavesNoContainer() {
        String loop = """
                public class Solution {
                    public static void main(String[] args) {
                        while (true) { }
                    }
                }
                """;

        SandboxResult result = sandbox.run(new SandboxRequest(loop, null, List.of()));

        String out = text(result);
        assertThat(out).containsPattern("@@COMPILE 0 0 \\d+\n");
        assertThat(out).containsPattern("@@CASE \\S+ \\d+ 1 \\d+ ");
        assertThat(out).endsWith("@@DONE\n");
        assertNoLeftoverContainer();
    }

    @Test
    void outputFlood_isCappedAndLeavesNoContainer() {
        String flood = """
                public class Solution {
                    public static void main(String[] args) {
                        String line = "x".repeat(1000);
                        while (true) {
                            System.out.println(line);
                        }
                    }
                }
                """;

        SandboxResult result = sandbox.run(new SandboxRequest(flood, null, List.of()));

        String out = text(result);
        Matcher header = Pattern.compile("@@CASE \\S+ \\d+ \\d+ \\d+ (\\d+) (\\d+)\n").matcher(out);
        assertThat(header.find()).as("case frame present").isTrue();
        assertThat(Integer.parseInt(header.group(1))).isLessThanOrEqualTo(PROPS.maxOutputBytes());
        assertThat(result.outputTruncated()).isFalse();
        assertThat(out).endsWith("@@DONE\n");
        assertNoLeftoverContainer();
    }
}