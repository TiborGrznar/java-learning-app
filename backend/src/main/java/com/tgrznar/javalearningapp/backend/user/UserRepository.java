package com.tgrznar.javalearningapp.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Used during login - email is the unique identifier for authentication. */
    Optional<User> findByEmail(String email);
}