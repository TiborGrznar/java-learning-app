package com.tgrznar.javalearningapp.learning.quiz;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Type-safe binding of the app.quiz.* settings from application.yaml. */
@ConfigurationProperties(prefix = "app.quiz")
@Validated
public record QuizProperties(

        // Share of correct answers (in percent) needed to pass a quiz.
        @Min(1) @Max(100)
        int passThresholdPercent,

        // Number of questions drawn from a module's question bank for one attempt.
        @Min(1) @Max(100)
        int questionsPerAttempt,

        // How long a loaded quiz can be submitted before it has to be loaded again.
        @NotNull
        Duration tokenTtl
) {
}