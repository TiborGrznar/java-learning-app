package com.tgrznar.javalearningapp.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Looks up a token by the hash of the raw value sent by the client. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revokes one token, but only if it is still active (compare-and-set in a single statement).
     * Returns 1 for the request that revoked it and 0 for any request that got there later, so a token
     * can be exchanged once, even when two requests with the same token arrive at the same moment.
     * Must run inside a transaction.
     */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.id = :id and t.revoked = false")
    int revokeIfActive(@Param("id") Long id);

    /** Revokes every token of the user that is still valid. Returns the number of revoked tokens. */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId);
}