package com.tgrznar.javalearningapp.codingtask;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodingTaskRepository extends JpaRepository<CodingTask, Long> {

    /** All coding tasks belonging to one module. */
    List<CodingTask> findByModuleId(Long moduleId);
}