package com.tgrznar.javalearningapp.userprogress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    /** The single progress row for a (user, module) pair - matches the DB unique constraint. */
    Optional<UserProgress> findByUserIdAndModuleId(Long userId, Long moduleId);

    /** All progress rows for a user, e.g. for an overall completion dashboard. */
    List<UserProgress> findByUserId(Long userId);
}