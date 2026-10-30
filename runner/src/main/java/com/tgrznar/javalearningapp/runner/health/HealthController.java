package com.tgrznar.javalearningapp.runner.health;

import com.tgrznar.javalearningapp.runner.sandbox.docker.DockerHealth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health probe, the only endpoint that needs no token. It answers 200 only while the Docker
 * daemon responds, otherwise 503, because a runner without Docker cannot run anything.
 * The body never says why Docker is unavailable.
 */
@RestController
public class HealthController {

    /** Response body of GET /health. */
    public record Health(String status) {
    }

    private final DockerHealth dockerHealth;

    public HealthController(DockerHealth dockerHealth) {
        this.dockerHealth = dockerHealth;
    }

    @GetMapping("/health")
    public ResponseEntity<Health> health() {
        if (dockerHealth.isDockerAvailable()) {
            return ResponseEntity.ok(new Health("UP"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new Health("DOCKER_UNAVAILABLE"));
    }
}