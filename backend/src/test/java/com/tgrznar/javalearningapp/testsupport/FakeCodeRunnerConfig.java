package com.tgrznar.javalearningapp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the HTTP code runner with a {@link FakeCodeRunner} in an integration test:
 * {@code @Import({TestcontainersConfiguration.class, FakeCodeRunnerConfig.class})}. The real client still exists
 * as a bean, but this one is primary, so nothing in a test ever calls a runner over the network.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FakeCodeRunnerConfig {

    @Bean
    @Primary
    public FakeCodeRunner fakeCodeRunner() {
        return new FakeCodeRunner();
    }
}
