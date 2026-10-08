package com.tgrznar.javalearningapp.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Looks up a token by the hash of the raw value sent by the client. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every token of the user that is still valid. Returns the number of revoked tokens. */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}