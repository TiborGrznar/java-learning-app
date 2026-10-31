package com.tgrznar.javalearningapp.codingtask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingTaskCaseRepository extends JpaRepository<CodingTaskCase, Long> {

    /** A task's cases in the order they are run and shown. */
    List<CodingTaskCase> findByCodingTaskIdOrderByOrderNumberAsc(Long codingTaskId);

    long countByCodingTaskId(Long codingTaskId);

    /** Removes all cases of a task; the caller must flush before inserting cases with the same numbers. */
    void deleteByCodingTaskId(Long codingTaskId);
}