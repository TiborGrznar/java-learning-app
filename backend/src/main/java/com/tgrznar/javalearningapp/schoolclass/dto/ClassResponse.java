package com.tgrznar.javalearningapp.schoolclass.dto;

import com.tgrznar.javalearningapp.schoolclass.SchoolClass;

import java.time.Instant;

/**
 * Administrator's view of a class, including the join code. Deliberately a separate type so that
 * entity internals never reach the API.
 */
public record ClassResponse(
        Long id,
        Long schoolId,
        String schoolName,
        Long teacherId,
        String teacherName,
        String name,
        String joinCode,
        boolean active,
        Instant createdAt
) {

    /**
     * Maps an entity to the response. Touches the lazy school and teacher associations,
     * so it must be called inside an open transaction (open-in-view is disabled).
     */
    public static ClassResponse from(SchoolClass schoolClass) {
        var school = schoolClass.getSchool();
        var teacher = schoolClass.getTeacher();
        return new ClassResponse(
                schoolClass.getId(),
                school.getId(),
                school.getName(),
                teacher.getId(),
                teacher.getName() + " " + teacher.getSurname(),
                schoolClass.getName(),
                schoolClass.getJoinCode(),
                schoolClass.isActive(),
                schoolClass.getCreatedAt()
        );
    }
}