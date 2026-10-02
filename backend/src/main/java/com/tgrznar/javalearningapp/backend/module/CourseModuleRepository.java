package com.tgrznar.javalearningapp.backend.module;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseModuleRepository extends JpaRepository<CourseModule, Long> {

    /** Active modules in the order they should be presented to students. */
    List<CourseModule> findByActiveTrueOrderByOrderNumberAsc();
}