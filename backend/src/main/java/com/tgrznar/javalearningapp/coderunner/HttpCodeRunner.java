package com.tgrznar.javalearningapp.coderunner;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Calls the code runner over HTTP. The runner is a separate service that executes the code in
 * disposable containers, nothing here ever runs student code.
 * <p>
 * Every answer is verified before it is returned, because the caller turns it into a verdict about a
 * student: a wrong number of results or an unknown status is a fault of the runner or of the
 * connection, never a result. No source code and no token is written to a log or to an exception.
 */
@Slf4j
@Component
public class HttpCodeRunner implements CodeRunner {

    static final String TOKEN_HEADER = "X-Runner-Token";

    private static final long DEFAULT_RETRY_AFTER_SECONDS = 5;
    private static final long MAX_RETRY_AFTER_SECONDS = 60;

    private final CodeRunnerProperties properties;
    private final HttpClient httpClient;

    public HttpCodeRunner(CodeRunnerProperties properties) {
        this.properties = properties;
        // HTTP/1.1 only: the runner is a plain Spring Boot service, and an upgrade attempt to HTTP/2
        // on a request with a body only adds a way to fail.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    @Override
    public RunnerResponse run(String solutionSource, String driverSource, List<String> caseInputs) {
        int expectedCases = Math.max(1, caseInputs.size());
        RunnerResponse response;
        try {
            response = clientFor(readTimeout(expectedCases)).post()
                    .uri("/run")
                    .header(TOKEN_HEADER, properties.token())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new RunnerRequest(solutionSource, driverSource, caseInputs))
                    .retrieve()
                    .body(RunnerResponse.class);
        } catch (RestClientResponseException e) {
            throw translate(e);
        } catch (RestClientException e) {
            // Connection refused, timeout, unreadable answer.
            throw new CodeRunnerUnavailableException("The code runner could not be reached or answered badly", e);
        }
        verify(response, expectedCases);
        return response;
    }

    /** The factory shares one HttpClient (and its connections), only the read timeout differs between calls. */
    private RestClient clientFor(Duration readTimeout) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .build();
    }

    private Duration readTimeout(int cases) {
        return properties.readTimeoutBase().plus(properties.readTimeoutPerCase().multipliedBy(cases));
    }

    private static RuntimeException translate(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 503) {
            return new CodeRunnerBusyException(retryAfterSeconds(e.getResponseHeaders()));
        }
        // 400, 401, 413 and 500 are all faults of the backend or its configuration (wrong token, request over
        // the limits, sandbox failure), not a verdict on the student's code.
        log.error("The code runner answered HTTP {}", status);
        return new CodeRunnerUnavailableException("The code runner answered HTTP " + status, e);
    }

    /** Reads Retry-After in seconds; a missing, unreadable or absurd value falls back to a sane one. */
    private static long retryAfterSeconds(HttpHeaders headers) {
        String value = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
        try {
            long seconds = Long.parseLong(value.trim());
            return Math.min(Math.max(seconds, 1), MAX_RETRY_AFTER_SECONDS);
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
    }

    /**
     * A successful compilation must come with exactly one result per input, numbered 1..n in order;
     * a failed one must come with none. Anything else cannot be turned into a trustworthy verdict.
     */
    private static void verify(RunnerResponse response, int expectedCases) {
        if (response == null || response.compile() == null || response.cases() == null) {
            throw broken("an empty or incomplete answer");
        }
        List<RunnerResponse.CaseResult> cases = response.cases();
        if (!response.compile().success()) {
            if (!cases.isEmpty()) {
                throw broken("results for a program that did not compile");
            }
            return;
        }
        if (cases.size() != expectedCases) {
            throw broken("a wrong number of results");
        }
        for (int i = 0; i < cases.size(); i++) {
            RunnerResponse.CaseResult result = cases.get(i);
            if (result == null || result.number() != i + 1 || result.status() == null) {
                throw broken("a result with a wrong number or status");
            }
        }
    }

    private static CodeRunnerUnavailableException broken(String what) {
        log.error("The code runner sent {}", what);
        return new CodeRunnerUnavailableException("The code runner sent " + what);
    }
}
