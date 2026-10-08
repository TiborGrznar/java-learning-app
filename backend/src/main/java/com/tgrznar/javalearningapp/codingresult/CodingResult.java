package com.tgrznar.javalearningapp.codingresult;

import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.user.model.User;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Result of one sandboxed execution of a user's submitted code against a CodingTask.
 */
@Entity
@Table(name = "coding_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodingResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coding_task_id", nullable = false)
    private CodingTask codingTask;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "submitted_code", nullable = false)
    private String submittedCode;

    @Column(nullable = false)
    private CodingResultStatus status;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "actual_output")
    private String actualOutput;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "executed_at", nullable = false, updatable = false)
    private Instant executedAt;
}