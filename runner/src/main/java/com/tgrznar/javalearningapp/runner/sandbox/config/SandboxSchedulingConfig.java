package com.tgrznar.javalearningapp.runner.sandbox.config;

import com.tgrznar.javalearningapp.runner.sandbox.docker.OrphanReaper;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled}, used by the {@link OrphanReaper}. */
@Configuration
@EnableScheduling
public class SandboxSchedulingConfig {
}