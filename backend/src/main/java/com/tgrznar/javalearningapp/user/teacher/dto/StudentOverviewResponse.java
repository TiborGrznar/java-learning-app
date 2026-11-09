package com.tgrznar.javalearningapp.user.teacher.dto;

import com.tgrznar.javalearningapp.common.PageResponse;

import java.util.List;

/**
 * The teacher's overview of students (UC-11): the active modules as columns and one page of students as rows.
 * The module headers are part of the response because a teacher has no other endpoint that lists modules.
 */
public record StudentOverviewResponse(List<ModuleHeader> modules, PageResponse<StudentOverviewRow> students) {
}
