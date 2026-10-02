package com.tgrznar.javalearningapp.backend.codingtask;

import com.tgrznar.javalearningapp.backend.module.CourseModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A coding exercise belonging to a CourseModule. The student submits code
 * (optionally starting from templateCode); the sandboxed runner's output
 * is compared against expectedOutput to evaluate the submission.
 */
@Entity
@Table(name = "coding_tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "module_id", nullable = false)
    private CourseModule module;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "expected_output", nullable = false)
    private String expectedOutput;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "template_code")
    private String templateCode;
}