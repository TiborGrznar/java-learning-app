package com.tgrznar.javalearningapp.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Looks up a token by the hash of the raw value sent by the client. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}