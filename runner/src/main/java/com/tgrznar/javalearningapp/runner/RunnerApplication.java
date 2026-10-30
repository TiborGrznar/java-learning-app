package com.tgrznar.javalearningapp.runner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The code runner: a separate service that is the only part of the system allowed to talk to Docker.
 * It has no database, no JWT secret and no user data, and it listens on localhost only.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RunnerApplication.class, args);
    }

}