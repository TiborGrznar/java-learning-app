package com.tgrznar.javalearningapp.codingresult;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    /**
     * For each of the given tasks the user has results of: how many have the given status and when the last
     * one was stored. Tasks without any result of the user are missing from the answer. Never reads code or output.
     */
    @Query("""
            select new com.tgrznar.javalearningapp.codingresult.TaskResultStats(
                    r.codingTask.id,
                    count(case when r.status = :passed then 1 end),
                    max(r.executedAt))
            from CodingResult r
            where r.user.id = :userId and r.codingTask.id in :taskIds
            group by r.codingTask.id
            """)
    List<TaskResultStats> summarizeByTask(@Param("userId") Long userId,
                                          @Param("taskIds") Collection<Long> taskIds,
                                          @Param("passed") CodingResultStatus passed);
}