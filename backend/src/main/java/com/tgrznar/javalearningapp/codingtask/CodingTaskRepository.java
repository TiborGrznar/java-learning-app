package com.tgrznar.javalearningapp.codingtask;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CodingTaskRepository extends JpaRepository<CodingTask, Long> {

    /** All coding tasks belonging to one module. */
    List<CodingTask> findByModuleId(Long moduleId);

    /** A module's tasks in the order students see them; the id breaks ties. */
    List<CodingTask> findByModuleIdOrderByOrderNumberAscIdAsc(Long moduleId);

    /** Scoping by module makes a task reached through another module's URL look like "not found". */
    Optional<CodingTask> findByIdAndModuleId(Long id, Long moduleId);

    boolean existsByModuleIdAndActiveTrue(Long moduleId);

    long countByModuleIdAndActiveTrue(Long moduleId);

    /** Number of submitted results of a task, from any student. */
    @Query("select count(r) from CodingResult r where r.codingTask.id = :taskId")
    long countResults(@Param("taskId") Long taskId);
}