package com.tgrznar.javalearningapp.quizquestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    /** All questions belonging to one module's quiz. */
    List<QuizQuestion> findByModuleId(Long moduleId);

    List<QuizQuestion> findByModuleIdOrderByIdAsc(Long moduleId);

    /** Scoping by module makes a question reached through another module's URL look like "not found". */
    Optional<QuizQuestion> findByIdAndModuleId(Long id, Long moduleId);
}