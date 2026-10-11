package com.tgrznar.javalearningapp.learning.quiz;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.random.RandomGenerator;

@Configuration
public class QuizConfig {

    /** Source of randomness for drawing and shuffling quiz questions. A bean so that tests can replace it. */
    @Bean
    public RandomGenerator quizRandomGenerator() {
        return RandomGenerator.getDefault();
    }
}