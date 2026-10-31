package com.tgrznar.javalearningapp.codingtask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingTaskCaseRepository extends JpaRepository<CodingTaskCase, Long> {

    /** All cases of a task in the order they are run and shown. */
    List<CodingTaskCase> findByCodingTaskIdOrderByOrderNumberAsc(Long codingTaskId);
}
