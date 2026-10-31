package com.tgrznar.javalearningapp.coderunner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests of the HTTP client against a real local HTTP server (the JDK's own), so the wire format, the
 * headers, the timeouts and the translation of every kind of failure are exercised for real.
 */
class HttpCodeRunnerTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    private static final String TWO_CASES = """
            {"compile":{"success":true,"timedOut":false,"message":""},
             "cases":[{"number":1,"status":"OK","millis":12,"stdout":"9\\n","message":""},
                      {"number":2,"status":"RUNTIME_ERROR","millis":30,"stdout":null,"message":"boom"}]}
            """;

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private HttpServer server;
    private volatile ExchangeHandler handler;
    private volatile String seenMethod;
    private volatile String seenPath;
    private volatile String seenToken;
    private volatile String seenContentType;
    private volatile String seenBody;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/run", exchange -> {
            seenMethod = exchange.getRequestMethod();
            seenPath = exchange.getRequestURI().getPath();
            seenToken = exchange.getRequestHeaders().getFirst("X-Runner-Token");
            seenContentType = exchange.getRequestHeaders().getFirst("Content-Type");
            seenBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            handler.handle(exchange);
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    // ---------- the request and a good answer ----------

    @Test
    void run_sendsTheRequestWithTheTokenAndParsesTheAnswer() {
        handler = exchange -> reply(exchange, 200, TWO_CASES);

        RunnerResponse response = runner().run("class Solution {}", "class Main {}", List.of("1\n", "2\n"));

        assertThat(seenMethod).isEqualTo("POST");
        assertThat(seenPath).isEqualTo("/run");
        assertThat(seenToken).isEqualTo(TOKEN);
        assertThat(seenContentType).startsWith("application/json");
        assertThat(seenBody)
                .contains("\"solutionSource\":\"class Solution {}\"")
                .contains("\"driverSource\":\"class Main {}\"")
                .contains("\"caseInputs\":[\"1\\n\",\"2\\n\"]");
        assertThat(response.compile().success()).isTrue();
        assertThat(response.cases()).hasSize(2);
        assertThat(response.cases().get(0).status()).isEqualTo(RunnerResponse.CaseStatus.OK);
        assertThat(response.cases().get(0).stdout()).isEqualTo("9\n");
        assertThat(response.cases().get(1).status()).isEqualTo(RunnerResponse.CaseStatus.RUNTIME_ERROR);
        assertThat(response.cases().get(1).message()).isEqualTo("boom");
    }

    @Test
    void run_compilationFailureWithoutCases_isReturnedAsIs() {
        handler = exchange -> reply(exchange, 200,
                "{\"compile\":{\"success\":false,\"timedOut\":false,\"message\":\"error: ';' expected\"},\"cases\":[]}");

        RunnerResponse response = runner().run("class Solution {", null, List.of("1\n"));

        assertThat(response.compile().success()).isFalse();
        assertThat(response.compile().message()).contains("';' expected");
        assertThat(response.cases()).isEmpty();
    }

    // ---------- the runner is busy ----------

    @Test
    void run_503WithRetryAfter_throwsBusyWithThatDelay() {
        handler = exchange -> reply(exchange, 503, "{\"code\":\"SANDBOX_BUSY\"}", "Retry-After", "7");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOfSatisfying(CodeRunnerBusyException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(7));
    }

    @Test
    void run_503WithoutRetryAfter_throwsBusyWithTheDefaultDelay() {
        handler = exchange -> reply(exchange, 503, "{\"code\":\"SANDBOX_BUSY\"}");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOfSatisfying(CodeRunnerBusyException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(5));
    }

    @Test
    void run_503WithUnreadableRetryAfter_throwsBusyWithTheDefaultDelay() {
        handler = exchange -> reply(exchange, 503, "{\"code\":\"SANDBOX_BUSY\"}", "Retry-After", "soon");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOfSatisfying(CodeRunnerBusyException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(5));
    }

    @Test
    void run_503WithAbsurdRetryAfter_isCapped() {
        handler = exchange -> reply(exchange, 503, "{\"code\":\"SANDBOX_BUSY\"}", "Retry-After", "86400");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOfSatisfying(CodeRunnerBusyException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
    }

    // ---------- the runner or the connection is at fault ----------

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 413, 500})
    void run_otherErrorStatuses_throwUnavailableWithoutLeakingTheToken(int status) {
        handler = exchange -> reply(exchange, status, "{\"code\":\"X\",\"message\":\"y\"}");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class)
                .hasMessageContaining(String.valueOf(status))
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void run_connectionRefused_throwsUnavailable() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        HttpCodeRunner runner = new HttpCodeRunner(properties("http://127.0.0.1:" + closedPort, Duration.ofSeconds(5)));

        assertThatThrownBy(() -> runner.run("class Solution {}", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class)
                .hasMessageNotContaining(TOKEN);
    }

    @Test
    void run_slowAnswer_throwsUnavailableAfterTheReadTimeout() {
        handler = exchange -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(exchange, 200, TWO_CASES);
        };
        HttpCodeRunner runner = new HttpCodeRunner(new CodeRunnerProperties(
                baseUrl(), TOKEN, Duration.ofSeconds(1), Duration.ofMillis(300), Duration.ZERO));
        long start = System.nanoTime();

        assertThatThrownBy(() -> runner.run("class Solution {}", null, List.of("1\n", "2\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @Test
    void run_answerThatIsNotJson_throwsUnavailable() {
        handler = exchange -> reply(exchange, 200, "this is not json");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void run_unknownCaseStatus_throwsUnavailable() {
        handler = exchange -> reply(exchange, 200, """
                {"compile":{"success":true,"timedOut":false,"message":""},
                 "cases":[{"number":1,"status":"EXPLODED","millis":1,"stdout":null,"message":""}]}
                """);

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    // ---------- the answer is verified ----------

    @Test
    void run_fewerResultsThanInputs_throwsUnavailable() {
        handler = exchange -> reply(exchange, 200, """
                {"compile":{"success":true,"timedOut":false,"message":""},
                 "cases":[{"number":1,"status":"OK","millis":1,"stdout":"1\\n","message":""}]}
                """);

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n", "2\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void run_resultsWithWrongNumbers_throwUnavailable() {
        handler = exchange -> reply(exchange, 200, """
                {"compile":{"success":true,"timedOut":false,"message":""},
                 "cases":[{"number":2,"status":"OK","millis":1,"stdout":"1\\n","message":""},
                          {"number":1,"status":"OK","millis":1,"stdout":"2\\n","message":""}]}
                """);

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n", "2\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void run_resultsForAProgramThatDidNotCompile_throwUnavailable() {
        handler = exchange -> reply(exchange, 200, """
                {"compile":{"success":false,"timedOut":false,"message":"error"},
                 "cases":[{"number":1,"status":"OK","millis":1,"stdout":"1\\n","message":""}]}
                """);

        assertThatThrownBy(() -> runner().run("class Solution {", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    @Test
    void run_answerWithoutCompileSection_throwsUnavailable() {
        handler = exchange -> reply(exchange, 200, "{\"cases\":[]}");

        assertThatThrownBy(() -> runner().run("class Solution {}", null, List.of("1\n")))
                .isInstanceOf(CodeRunnerUnavailableException.class);
    }

    // ---------- helpers ----------

    private HttpCodeRunner runner() {
        return new HttpCodeRunner(properties(baseUrl(), Duration.ofSeconds(5)));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static CodeRunnerProperties properties(String baseUrl, Duration readTimeoutBase) {
        return new CodeRunnerProperties(baseUrl, TOKEN, Duration.ofSeconds(1), readTimeoutBase, Duration.ofSeconds(1));
    }

    private static void reply(HttpExchange exchange, int status, String body, String... headers) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        for (int i = 0; i + 1 < headers.length; i += 2) {
            exchange.getResponseHeaders().add(headers[i], headers[i + 1]);
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
