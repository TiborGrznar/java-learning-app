package com.tgrznar.javalearningapp.runner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The runner must not start without a proper shared secret. */
class RunnerPropertiesTest {

    private static final String[] NO_WEB_SERVER = {"--spring.main.web-application-type=none"};

    @Test
    void startup_withShortToken_fails() {
        assertThatThrownBy(() -> SpringApplication.run(RunnerApplication.class,
                "--runner.token=too-short", NO_WEB_SERVER[0]))
                .hasStackTraceContaining("runner.token");
    }

    @Test
    void startup_withBlankToken_fails() {
        assertThatThrownBy(() -> SpringApplication.run(RunnerApplication.class,
                "--runner.token=", NO_WEB_SERVER[0]))
                .hasStackTraceContaining("runner.token");
    }
}