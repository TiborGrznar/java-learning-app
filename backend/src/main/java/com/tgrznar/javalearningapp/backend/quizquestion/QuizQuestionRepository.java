package com.tgrznar.javalearningapp.backend.quizquestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /** All questions belonging to one module's quiz. */
    List<QuizQuestion> findByModuleId(Long moduleId);
}