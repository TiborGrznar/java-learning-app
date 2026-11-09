package com.tgrznar.javalearningapp.userprogress;

/**
 * The three completion flags of one student in one module, read straight from the database without loading
 * the user or the module. Wrapper types, because the JPQL constructor expression that fills it is the safest with them.
 */
public record ProgressFlagsRow(
        Long userId,
        Long moduleId,
        Boolean theoryCompleted,
        Boolean quizCompleted,
        Boolean codingCompleted
) {
}
