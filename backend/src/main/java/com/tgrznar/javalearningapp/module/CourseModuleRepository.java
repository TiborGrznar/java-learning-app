package com.tgrznar.javalearningapp.module;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    /** Active modules in the order they should be presented to students. */
    List<CourseModule> findByActiveTrueOrderByOrderNumberAsc();

    /** Active modules in the given sort order, so the student list can break ties by id. */
    List<CourseModule> findByActiveTrue(Sort sort);
}