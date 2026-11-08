package com.tgrznar.javalearningapp.learning.progress;

import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingresult.TaskResultStats;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.learning.ModuleAccessService;
import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.progress.dto.CodingProgress;
import com.tgrznar.javalearningapp.learning.progress.dto.LastQuizResult;
import com.tgrznar.javalearningapp.learning.progress.dto.ModuleProgress;
import com.tgrznar.javalearningapp.learning.progress.dto.ProgressResponse;
import com.tgrznar.javalearningapp.learning.progress.dto.TaskProgress;
import com.tgrznar.javalearningapp.learning.progress.dto.TaskState;
import com.tgrznar.javalearningapp.quizresult.LastQuizRow;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the student's own progress overview (UC-10) from what the student already sees elsewhere:
 * active modules and their lock state ({@link ModuleAccessService}), the latest quiz result and the
 * state of the active coding tasks.
 * <p>
 * The number of queries does not depend on the number of modules: the modules and the progress rows, the
 * latest quiz results, the active tasks of all modules whose coding part is visible, and one aggregate
 * over the student's results of those tasks.
 * <p>
 * Only status values, counts and times are read from the results. Submitted code, program output and error
 * messages are never loaded here, so this endpoint cannot show anything the student did not see.
 */
@Service
@RequiredArgsConstructor
public class StudentProgressService {

    private final ModuleAccessService accessService;
    private final QuizResultRepository quizResultRepository;
    private final CodingTaskRepository taskRepository;
    private final CodingResultRepository codingResultRepository;

    @Transactional(readOnly = true)
    public ProgressResponse get(Long userId) {
        List<ModuleAccessService.ModuleAccess> accesses = accessService.accessFor(userId);

        Map<Long, LastQuizRow> lastQuizByModule = quizResultRepository.findLastResultsPerModule(userId).stream()
                .collect(Collectors.toMap(LastQuizRow::moduleId, Function.identity()));

        Map<Long, List<CodingTask>> tasksByModule = activeTasksByModule(accesses);
        Map<Long, TaskResultStats> statsByTask = statsByTask(userId, tasksByModule);

        List<ModuleProgress> modules = accesses.stream()
                .map(access -> toModuleProgress(access, lastQuizByModule, tasksByModule, statsByTask))
                .toList();
        int completed = (int) modules.stream().filter(module -> module.status() == ModuleStatus.COMPLETED).count();
        return new ProgressResponse(completed, modules.size(), modules);
    }

    /** Tasks are loaded only for modules whose coding part the student may see, in one query. */
    private Map<Long, List<CodingTask>> activeTasksByModule(List<ModuleAccessService.ModuleAccess> accesses) {
        List<Long> moduleIds = accesses.stream()
                .filter(StudentProgressService::codingVisible)
                .map(access -> access.module().getId())
                .toList();
        if (moduleIds.isEmpty()) {
            return Map.of();
        }
        return taskRepository.findByModuleIdInAndActiveTrueOrderByOrderNumberAscIdAsc(moduleIds).stream()
                .collect(Collectors.groupingBy(task -> task.getModule().getId()));
    }

    private Map<Long, TaskResultStats> statsByTask(Long userId, Map<Long, List<CodingTask>> tasksByModule) {
        List<Long> taskIds = tasksByModule.values().stream()
                .flatMap(List::stream)
                .map(CodingTask::getId)
                .toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return codingResultRepository.summarizeByTask(userId, taskIds, CodingResultStatus.PASSED).stream()
                .collect(Collectors.toMap(TaskResultStats::taskId, Function.identity()));
    }

    private static ModuleProgress toModuleProgress(ModuleAccessService.ModuleAccess access,
                                                   Map<Long, LastQuizRow> lastQuizByModule,
                                                   Map<Long, List<CodingTask>> tasksByModule,
                                                   Map<Long, TaskResultStats> statsByTask) {
        Long moduleId = access.module().getId();
        UserProgress progress = access.progress();

        LastQuizRow quiz = lastQuizByModule.get(moduleId);
        LastQuizResult lastQuiz = quiz == null
                ? null
                : new LastQuizResult(quiz.score(), quiz.maxScore(), quiz.completedAt());

        CodingProgress coding = codingVisible(access)
                ? toCodingProgress(tasksByModule.getOrDefault(moduleId, List.of()), statsByTask)
                : null;

        return new ModuleProgress(
                moduleId,
                access.module().getTitle(),
                access.module().getOrderNumber(),
                ModuleStatus.of(progress),
                access.locked(),
                progress != null && progress.isTheoryCompleted(),
                progress != null && progress.isQuizCompleted(),
                progress != null && progress.isCodingCompleted(),
                lastQuiz,
                coding);
    }

    private static CodingProgress toCodingProgress(List<CodingTask> tasks, Map<Long, TaskResultStats> statsByTask) {
        List<TaskProgress> taskProgress = tasks.stream()
                .map(task -> toTaskProgress(task, statsByTask.get(task.getId())))
                .toList();
        int passed = (int) taskProgress.stream().filter(task -> task.state() == TaskState.PASSED).count();
        return new CodingProgress(taskProgress.size(), passed, taskProgress);
    }

    private static TaskProgress toTaskProgress(CodingTask task, TaskResultStats stats) {
        if (stats == null) {
            return new TaskProgress(task.getId(), task.getOrderNumber(), task.getTitle(), TaskState.NOT_ATTEMPTED, null);
        }
        TaskState state = stats.passedCount() > 0 ? TaskState.PASSED : TaskState.FAILED;
        return new TaskProgress(task.getId(), task.getOrderNumber(), task.getTitle(), state, stats.lastAttemptAt());
    }

    /** The same rule as the list of coding tasks: the module is unlocked and its theory read. */
    private static boolean codingVisible(ModuleAccessService.ModuleAccess access) {
        return !access.locked() && access.progress() != null && access.progress().isTheoryCompleted();
    }
}
