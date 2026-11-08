package com.tgrznar.javalearningapp.quizresult;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

    /** A user's quiz result history, e.g. for a personal progress overview. */
    List<QuizResult> findByUserId(Long userId);

    /**
     * The latest result of the user in every module that has one (the highest id of the module, ids grow
     * with time). Only the numbers and the time are read.
     */
    @Query("""
            select new com.tgrznar.javalearningapp.quizresult.LastQuizRow(r.module.id, r.score, r.maxScore, r.completedAt)
            from QuizResult r
            where r.user.id = :userId
              and r.id = (select max(l.id) from QuizResult l where l.user.id = :userId and l.module.id = r.module.id)
            """)
    List<LastQuizRow> findLastResultsPerModule(@Param("userId") Long userId);
}