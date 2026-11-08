package com.tgrznar.javalearningapp.learning.progress.dto;

import java.util.List;

/**
 * The coding part of a module. Counts and the list cover the tasks that are active now, so a deactivated
 * task is neither listed nor counted.
 */
public record CodingProgress(int activeTasks, int passedTasks, List<TaskProgress> tasks) {
}
