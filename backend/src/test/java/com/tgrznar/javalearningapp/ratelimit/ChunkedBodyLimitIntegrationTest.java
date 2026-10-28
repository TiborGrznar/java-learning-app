package com.tgrznar.javalearningapp.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tgrznar.javalearningapp.testsupport.TestcontainersConfiguration;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * A body sent in chunks has no Content-Length, so only the counting stream can stop it. This runs
 * against the real embedded server to prove that the exception thrown inside the stream ends as 413.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ChunkedBodyLimitIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> postChunked(String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .version(HttpClient.Version.HTTP_1_1)
                .header("Content-Type", "application/json")
                // An input stream publisher has an unknown length, so the client uses chunked transfer.
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void chunkedBodyOverTheLimit_isRefusedWith413() throws Exception {
        String body = "{\"email\":\"" + "a".repeat(20_000) + "\",\"password\":\"x\"}";

        HttpResponse<String> response = postChunked(body);

        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(response.body()).contains("PAYLOAD_TOO_LARGE");
    }

    @Test
    void chunkedBodyWithinTheLimit_isProcessedNormally() throws Exception {
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"whatever-password-1\"}";

        HttpResponse<String> response = postChunked(body);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("INVALID_CREDENTIALS");
    }
}