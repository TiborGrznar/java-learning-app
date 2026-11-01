package com.tgrznar.javalearningapp.codingresult;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

public interface CodingResultRepository extends JpaRepository<CodingResult, Long> {

    /** A user's submission history for one coding task. */
    List<CodingResult> findByUserIdAndCodingTaskId(Long userId, Long codingTaskId);

    /** Ids of the tasks of a module in which the user has at least one result with the given status. */
    @Query("select distinct r.codingTask.id from CodingResult r "
            + "where r.user.id = :userId and r.codingTask.module.id = :moduleId and r.status = :status")
    Set<Long> findTaskIdsWithStatus(@Param("userId") Long userId,
                                    @Param("moduleId") Long moduleId,
                                    @Param("status") CodingResultStatus status);
}