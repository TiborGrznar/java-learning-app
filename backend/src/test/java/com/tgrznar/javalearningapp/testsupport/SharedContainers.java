package com.tgrznar.javalearningapp.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mysql.MySQLContainer;

/**
 * Containers shared by all integration tests. The field of an interface is static, so there is
 * one MySQL container for the whole test run, no matter how many Spring contexts the tests create.
 * The image matches docker-compose.yml and the production database.
 */
public interface SharedContainers {

    @Container
    @ServiceConnection
    MySQLContainer MYSQL = new MySQLContainer("mysql:8.0");
}