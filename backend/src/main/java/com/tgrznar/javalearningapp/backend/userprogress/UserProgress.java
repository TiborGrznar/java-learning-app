package com.tgrznar.javalearningapp.backend.userprogress;

import com.tgrznar.javalearningapp.backend.module.CourseModule;
import com.tgrznar.javalearningapp.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Tracks one user's completion progress (theory / quiz / coding) within a single
 * CourseModule. Exactly one row per (user, module) pair - enforced by a unique
 * constraint at the database level.
 */
@Entity
@Table(
        name = "user_progress",
        uniqueConstraints = @UniqueConstraint(name = "uq_user_progress_user_module", columnNames = {"user_id", "module_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(name = "theory_completed", nullable = false)
    private boolean theoryCompleted = false;

    @Column(name = "quiz_completed", nullable = false)
    private boolean quizCompleted = false;

    @Column(name = "coding_completed", nullable = false)
    private boolean codingCompleted = false;

    /** Refreshed by Hibernate on every insert AND update, matching the DB's ON UPDATE CURRENT_TIMESTAMP. */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}