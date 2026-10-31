package com.tgrznar.javalearningapp.coderunner;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** The runner settings are bound and validated like at startup: a bad token must stop the application. */
class CodeRunnerPropertiesTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef";

    @EnableConfigurationProperties(CodeRunnerProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void validSettings_areBound() {
        runner.withPropertyValues(
                        "app.runner.base-url=http://127.0.0.1:8081",
                        "app.runner.token=" + TOKEN,
                        "app.runner.connect-timeout=2s",
                        "app.runner.read-timeout-base=25s",
                        "app.runner.read-timeout-per-case=5s")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    CodeRunnerProperties properties = context.getBean(CodeRunnerProperties.class);
                    assertThat(properties.baseUrl()).isEqualTo("http://127.0.0.1:8081");
                    assertThat(properties.token()).isEqualTo(TOKEN);
                    assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.readTimeoutBase()).isEqualTo(Duration.ofSeconds(25));
                    assertThat(properties.readTimeoutPerCase()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void tokenOfThirtyOneCharacters_stopsTheStartup() {
        runner.withPropertyValues(validWith("app.runner.token=" + TOKEN.substring(1)))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void missingToken_stopsTheStartup() {
        runner.withPropertyValues(
                        "app.runner.base-url=http://127.0.0.1:8081",
                        "app.runner.connect-timeout=2s",
                        "app.runner.read-timeout-base=25s",
                        "app.runner.read-timeout-per-case=5s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankBaseUrl_stopsTheStartup() {
        runner.withPropertyValues(validWith("app.runner.base-url="))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void toString_doesNotShowTheToken() {
        CodeRunnerProperties properties = new CodeRunnerProperties(
                "http://127.0.0.1:8081", TOKEN, Duration.ofSeconds(2), Duration.ofSeconds(25), Duration.ofSeconds(5));

        assertThat(properties.toString()).doesNotContain(TOKEN).contains("http://127.0.0.1:8081");
    }

    /** All valid settings except the one given, which replaces its valid counterpart. */
    private static String[] validWith(String override) {
        String key = override.substring(0, override.indexOf('='));
        String[] valid = {
                "app.runner.base-url=http://127.0.0.1:8081",
                "app.runner.token=" + TOKEN,
                "app.runner.connect-timeout=2s",
                "app.runner.read-timeout-base=25s",
                "app.runner.read-timeout-per-case=5s"};
        for (int i = 0; i < valid.length; i++) {
            if (valid[i].startsWith(key + "=")) {
                valid[i] = override;
            }
        }
        return valid;
    }
}
