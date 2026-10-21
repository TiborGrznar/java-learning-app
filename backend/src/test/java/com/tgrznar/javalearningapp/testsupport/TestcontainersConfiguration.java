package com.tgrznar.javalearningapp.testsupport;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;

/**
 * Starts the shared containers and points the datasource at them. It also registers the shared
 * test fixtures ({@link TestUsers}, {@link EnrolledClassFactory}). Import it into an integration test
 * with {@code @Import(TestcontainersConfiguration.class)}.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(SharedContainers.class)
@Import({TestUsers.class, EnrolledClassFactory.class})
public class TestcontainersConfiguration {
}