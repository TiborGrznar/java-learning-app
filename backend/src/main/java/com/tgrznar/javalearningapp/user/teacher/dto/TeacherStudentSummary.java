package com.tgrznar.javalearningapp.user.teacher.dto;

/**
 * Who a student is, as the teacher sees them in the detail of the student's progress. Deliberately a separate
 * type with a fixed list of fields: no entity, no password hash, no join code.
 *
 * @param active      false for a deactivated student
 * @param classActive false when the class was deactivated (its students keep their access)
 */
public record TeacherStudentSummary(
        Long studentId,
        String name,
        String surname,
        String email,
        boolean active,
        Long classId,
        String className,
        boolean classActive
) {
}
