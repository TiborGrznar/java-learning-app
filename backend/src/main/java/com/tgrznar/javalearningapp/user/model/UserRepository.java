package com.tgrznar.javalearningapp.user.model;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Used during login - email is the unique identifier for authentication. */
    Optional<User> findByEmail(String email);

    /** Used by registration to reject a duplicate e-mail before attempting an insert. */
    boolean existsByEmail(String email);

    /** Used when editing a user: the e-mail may collide with anyone except the user themselves. */
    boolean existsByEmailAndIdNot(String email, Long id);

    /** Used at startup to decide whether the first administrator has to be created. */
    boolean existsByRole(UserRole role);

    /** Used to protect the last active administrator. */
    long countByRoleAndActive(UserRole role, boolean active);

    /** Cheap membership check for the enrollment gate: true when the user has a class. */
    boolean existsByIdAndSchoolClassIsNotNull(Long id);

    /** Administrator's user list. A null filter means "any". School and class are fetched to avoid N+1 queries. */
    @Query(value = """
            select u from User u left join fetch u.school left join fetch u.schoolClass
            where (:schoolId is null or u.school.id = :schoolId)
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """,
            countQuery = """
            select count(u) from User u
            where (:schoolId is null or u.school.id = :schoolId)
              and (:role is null or u.role = :role)
              and (:active is null or u.active = :active)
            """)
    Page<User> search(@Param("schoolId") Long schoolId,
                      @Param("role") UserRole role,
                      @Param("active") Boolean active,
                      Pageable pageable);

    /**
     * Students of the classes a teacher leads in his current school (UC-11). A null filter means "any". The
     * class is fetched together with the student, and the order is part of the query because a sort by the
     * fetched association would add a second join. The role is a parameter (always STUDENT), so no teacher or
     * administrator can ever appear in the result.
     */
    @Query(value = """
            select u from User u join fetch u.schoolClass c
            where u.role = :role
              and c.teacher.id = :teacherId
              and c.school.id = :schoolId
              and (:classId is null or c.id = :classId)
              and (:active is null or u.active = :active)
            order by c.name, u.surname, u.name, u.id
            """,
            countQuery = """
            select count(u) from User u join u.schoolClass c
            where u.role = :role
              and c.teacher.id = :teacherId
              and c.school.id = :schoolId
              and (:classId is null or c.id = :classId)
              and (:active is null or u.active = :active)
            """)
    Page<User> findTeacherStudents(@Param("teacherId") Long teacherId,
                                   @Param("schoolId") Long schoolId,
                                   @Param("role") UserRole role,
                                   @Param("classId") Long classId,
                                   @Param("active") Boolean active,
                                   Pageable pageable);
}