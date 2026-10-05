package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.school.School;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * An application user - a student, teacher, or admin.
 * Optionally linked to a {@link School}; students/teachers are typically
 * assigned one, while an admin account may not be tied to any school.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    private String surname;

    /** Used as the login identifier; uniqueness is enforced at the database level. */
    @Column(nullable = false, unique = true)
    private String email;

    /** BCrypt hash of the user's password. The plaintext password is never stored or logged. */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private UserRole role;

    /** Nullable - not every account (e.g. an admin) has to belong to a school. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "school_id")
    private School school;

    /**
     * Soft-delete / deactivation flag. Field is named "active" rather than "isActive"
     * so Lombok generates isActive()/setActive() instead of the awkward isIsActive().
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}