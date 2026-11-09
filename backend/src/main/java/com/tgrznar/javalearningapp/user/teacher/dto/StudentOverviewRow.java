package com.tgrznar.javalearningapp.user.teacher.dto;

import java.util.List;

/**
 * One student in the teacher's overview. Deliberately a separate type with a fixed list of fields:
 * no entity, no password hash, no submitted code, program output or error messages, no join code.
 *
 * @param active          false for a deactivated student, who stays in the overview so the teacher can reactivate them
 * @param classActive     false when the class was deactivated (its students keep their access)
 * @param completedModules number of active modules in status COMPLETED
 * @param modules         one cell per module header, in the order of the headers
 */
public record StudentOverviewRow(
        Long studentId,
        String name,
        String surname,
        String email,
        boolean active,
        Long classId,
        String className,
        boolean classActive,
        int completedModules,
        List<ModuleCell> modules
) {
}
