package com.tgrznar.javalearningapp.user.teacher.dto;

import com.tgrznar.javalearningapp.learning.progress.dto.ProgressResponse;

/**
 * The detail of one student's progress for the teacher (UC-11): who the student is and the same progress
 * the student sees in his own overview.
 */
public record TeacherStudentProgressResponse(TeacherStudentSummary student, ProgressResponse progress) {
}
