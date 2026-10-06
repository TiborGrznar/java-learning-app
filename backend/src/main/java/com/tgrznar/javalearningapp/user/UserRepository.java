package com.tgrznar.javalearningapp.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Used during login - email is the unique identifier for authentication. */
    Optional<User> findByEmail(String email);

    /** Used by registration to reject a duplicate e-mail before attempting an insert. */
    boolean existsByEmail(String email);

    /** Used at startup to decide whether the first administrator has to be created. */
    boolean existsByRole(UserRole role);
}