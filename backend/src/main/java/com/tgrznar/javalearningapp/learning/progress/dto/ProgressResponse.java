package com.tgrznar.javalearningapp.learning.progress.dto;

import java.util.List;

/**
 * The student's own progress (UC-10): active modules in course order and how many of them are completed.
 */
public record ProgressResponse(int completedModules, int totalModules, List<ModuleProgress> modules) {
}
