package com.tgrznar.javalearningapp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

/**
 * Starts the shared containers and points the datasource at them. Import it into an integration
 * test with {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(SharedContainers.class)
public class TestcontainersConfiguration {
}