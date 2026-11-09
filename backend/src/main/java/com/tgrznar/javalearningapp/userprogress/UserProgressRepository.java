package com.tgrznar.javalearningapp.userprogress;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {

    /** The single progress row for a (user, module) pair - matches the DB unique constraint. */
    Optional<UserProgress> findByUserIdAndModuleId(Long userId, Long moduleId);

    /**
     * The same row, locked for writing until the end of the transaction. Whoever stores a coding result
     * takes the lock first, so concurrent submissions of one student are decided one after another.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProgress p where p.user.id = :userId and p.module.id = :moduleId")
    Optional<UserProgress> findForUpdate(@Param("userId") Long userId, @Param("moduleId") Long moduleId);

    /** All progress rows for a user, e.g. for an overall completion dashboard. */
    List<UserProgress> findByUserId(Long userId);

    /**
     * The completion flags of several users in one query, e.g. for the teacher's overview. Users without any
     * progress are missing from the answer. Only the flags are read, never the entities.
     */
    @Query("""
            select new com.tgrznar.javalearningapp.userprogress.ProgressFlagsRow(
                    p.user.id, p.module.id, p.theoryCompleted, p.quizCompleted, p.codingCompleted)
            from UserProgress p
            where p.user.id in :userIds
            """)
    List<ProgressFlagsRow> findFlagsByUserIdIn(@Param("userIds") Collection<Long> userIds);
}