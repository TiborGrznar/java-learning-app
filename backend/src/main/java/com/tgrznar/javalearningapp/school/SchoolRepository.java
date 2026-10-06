package com.tgrznar.javalearningapp.school;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SchoolRepository extends JpaRepository<School, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<School> findAllByOrderByNameAsc();

    List<School> findAllByActiveOrderByNameAsc(boolean active);
}