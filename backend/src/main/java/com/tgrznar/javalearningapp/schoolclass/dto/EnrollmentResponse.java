package com.tgrznar.javalearningapp.schoolclass.dto;

import com.tgrznar.javalearningapp.schoolclass.SchoolClass;

/** The class a student has just joined. The join code is deliberately not part of it. */
public record EnrollmentResponse(Long classId, String className, Long schoolId, String schoolName) {

    public static EnrollmentResponse from(SchoolClass schoolClass) {
        return new EnrollmentResponse(
                schoolClass.getId(),
                schoolClass.getName(),
                schoolClass.getSchool().getId(),
                schoolClass.getSchool().getName());
    }
}