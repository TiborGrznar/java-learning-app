package com.tgrznar.javalearningapp.codingtask;

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
 * One test case of a CodingTask: the text sent to the program's standard input and the output
 * the program must print. A hidden case is never shown to students, its input and expected output
 * stay in the backend and the student only learns whether the case passed.
 */
@Entity
@Table(name = "coding_task_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CodingTaskCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coding_task_id", nullable = false)
    private CodingTask codingTask;

    /** Position of the case within its task, unique per task. */
    @Column(name = "order_number", nullable = false)
    private int orderNumber;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "input_text", nullable = false)
    private String inputText;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "expected_output", nullable = false)
    private String expectedOutput;

    /** Named "hidden" for the same Lombok getter reason as "active" in User. Cases are hidden unless set otherwise. */
    @Column(name = "is_hidden", nullable = false)
    private boolean hidden = true;
}
