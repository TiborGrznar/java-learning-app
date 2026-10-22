package com.tgrznar.javalearningapp.runner.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe, the only endpoint that needs no token. It says nothing about Docker yet;
 * the check that Docker answers is added together with the Docker integration.
 */
@RestController
public class HealthController {

    /** Response body of GET /health. */
    public record Health(String status) {
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("UP");
    }
}