package com.tgrznar.javalearningapp.schoolclass;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, Long> {

    /** Used when generating a join code; the column is case-insensitive, so the comparison is too. */
    boolean existsByJoinCode(String joinCode);

    /** Class names are unique within a school, regardless of letter case. */
    boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

    /** Used when renaming a class: the name may collide with any other class of the school, not with itself. */
    boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

    /** Used to protect a teacher: whoever leads an active class must not be deactivated, demoted or moved. */
    boolean existsByTeacherIdAndActive(Long teacherId, boolean active);

    /**
     * A class of the given teacher. Ownership is part of the query on purpose: a teacher must never be able to
     * read or change a class of someone else, and the join code is a credential.
     */
    Optional<SchoolClass> findByIdAndTeacherId(Long id, Long teacherId);

    /**
     * The class a student can join with this code: the class and its school must both be active. The school is
     * fetched because the enrollment response and the school check need it.
     */
    @Query("""
            select c from SchoolClass c join fetch c.school
            where c.joinCode = :joinCode and c.active = true and c.school.active = true
            """)
    Optional<SchoolClass> findJoinableByJoinCode(@Param("joinCode") String joinCode);

    /** Administrator's class list. A null filter means "any". School and teacher are fetched to avoid N+1 queries. */
    @Query("""
            select c from SchoolClass c join fetch c.school join fetch c.teacher
            where (:schoolId is null or c.school.id = :schoolId)
              and (:teacherId is null or c.teacher.id = :teacherId)
              and (:active is null or c.active = :active)
            order by c.name, c.id
            """)
    List<SchoolClass> search(@Param("schoolId") Long schoolId,
                             @Param("teacherId") Long teacherId,
                             @Param("active") Boolean active);
}