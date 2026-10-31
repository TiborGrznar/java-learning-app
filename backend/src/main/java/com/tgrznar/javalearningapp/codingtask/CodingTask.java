package com.tgrznar.javalearningapp.codingtask;

import com.tgrznar.javalearningapp.module.CourseModule;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A coding exercise belonging to a CourseModule. The student submits code
 * (optionally starting from templateCode); the sandboxed runner executes it against
 * the task's CodingTaskCase entries and the backend compares the output with each
 * case's expected output.
 * <p>
 * driverCode and referenceSolution are written and read by administrators only,
 * they must never be part of a student-facing response.
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

    /** Position of the task within its module. */
    @Column(name = "order_number", nullable = false)
    private int orderNumber;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "template_code")
    private String templateCode;

    /** Optional Main.java written by the task author, it reads stdin and calls the student's method. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "driver_code")
    private String driverCode;

    /** A solution known to pass every case, run against the cases before the task is activated. */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "reference_solution")
    private String referenceSolution;

    /** Visibility flag, named "active" for the same Lombok getter reason as in User. A new task starts inactive. */
    @Column(name = "is_active", nullable = false)
    private boolean active = false;
}