package com.tgrznar.javalearningapp.backend.codingresult;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingResultRepository extends JpaRepository<CodingResult, Long> {

    /** A user's submission history for one coding task. */
    List<CodingResult> findByUserIdAndCodingTaskId(Long userId, Long codingTaskId);
}