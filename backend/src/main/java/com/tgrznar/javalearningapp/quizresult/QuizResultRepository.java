package com.tgrznar.javalearningapp.quizresult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

    /** A user's quiz result history, e.g. for a personal progress overview. */
    List<QuizResult> findByUserId(Long userId);
}