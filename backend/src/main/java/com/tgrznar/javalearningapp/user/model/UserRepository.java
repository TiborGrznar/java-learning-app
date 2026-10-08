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

    /** Administrator's user list. A null filter means "any". The school is fetched to avoid N+1 queries. */
    @Query(value = """
            select u from User u left join fetch u.school
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
}