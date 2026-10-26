package com.tgrznar.javalearningapp.testsupport;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The integration tests must run on the same MySQL image as local development, otherwise a
 * migration could pass in the tests and fail in real use (or the other way round).
 * Reads docker-compose.yml from the repository root; Maven and IntelliJ run the tests in backend/.
 */
class DatabaseVersionConsistencyTest {

    private static final Pattern COMPOSE_IMAGE = Pattern.compile("(?m)^\\s*image:\\s*(mysql:\\S+)\\s*$");

    @Test
    void composeAndTestcontainersUseTheSameMysqlImage() throws IOException {
        String compose = Files.readString(Path.of("..", "docker-compose.yml"));

        Matcher matcher = COMPOSE_IMAGE.matcher(compose);

        assertThat(matcher.find()).as("a mysql image line in docker-compose.yml").isTrue();
        assertThat(SharedContainers.MYSQL.getDockerImageName()).isEqualTo(matcher.group(1));
    }
}