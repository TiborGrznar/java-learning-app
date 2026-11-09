package com.tgrznar.javalearningapp.user.teacher.dto;

/** A column of the teacher's overview: one active module of the course. */
public record ModuleHeader(Long moduleId, String title, int orderNumber) {
}
