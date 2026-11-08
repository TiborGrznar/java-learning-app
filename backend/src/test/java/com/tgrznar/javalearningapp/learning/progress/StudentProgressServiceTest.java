package com.tgrznar.javalearningapp.learning.progress;

import com.tgrznar.javalearningapp.codingresult.CodingResultRepository;
import com.tgrznar.javalearningapp.codingresult.CodingResultStatus;
import com.tgrznar.javalearningapp.codingresult.TaskResultStats;
import com.tgrznar.javalearningapp.codingtask.CodingTask;
import com.tgrznar.javalearningapp.codingtask.CodingTaskRepository;
import com.tgrznar.javalearningapp.learning.ModuleAccessService;
import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.progress.dto.ModuleProgress;
import com.tgrznar.javalearningapp.learning.progress.dto.ProgressResponse;
import com.tgrznar.javalearningapp.learning.progress.dto.TaskProgress;
import com.tgrznar.javalearningapp.learning.progress.dto.TaskState;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.quizresult.LastQuizRow;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentProgressServiceTest {

    private static final Long USER_ID = 5L;
    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Mock
    private ModuleAccessService accessService;

    @Mock
    private QuizResultRepository quizResultRepository;

    @Mock
    private CodingTaskRepository taskRepository;

    @Mock
    private CodingResultRepository codingResultRepository;

    private StudentProgressService service;

    @BeforeEach
    void setUp() {
        service = new StudentProgressService(accessService, quizResultRepository, taskRepository, codingResultRepository);
    }

    @Test
    void get_withoutModules_returnsEmptyProgressAndLoadsNoCodingData() {
        when(accessService.accessFor(USER_ID)).thenReturn(List.of());

        ProgressResponse result = service.get(USER_ID);

        assertThat(result.completedModules()).isZero();
        assertThat(result.totalModules()).isZero();
        assertThat(result.modules()).isEmpty();
        verifyNoInteractions(taskRepository, codingResultRepository);
    }

    @Test
    void get_countsOnlyCompletedModules() {
        CourseModule first = module(1L);
        CourseModule second = module(2L);
        CourseModule third = module(3L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(first, progress(first, true, true, true), false),
                new ModuleAccessService.ModuleAccess(second, progress(second, true, true, false), false),
                new ModuleAccessService.ModuleAccess(third, null, true)));
        when(taskRepository.findByModuleIdInAndActiveTrueOrderByOrderNumberAscIdAsc(List.of(1L, 2L)))
                .thenReturn(List.of());

        ProgressResponse result = service.get(USER_ID);

        assertThat(result.completedModules()).isEqualTo(1);
        assertThat(result.totalModules()).isEqualTo(3);
        assertThat(result.modules()).extracting(ModuleProgress::status)
                .containsExactly(ModuleStatus.COMPLETED, ModuleStatus.IN_PROGRESS, ModuleStatus.NOT_STARTED);
        assertThat(result.modules()).extracting(ModuleProgress::locked).containsExactly(false, false, true);
        verifyNoInteractions(codingResultRepository);
    }

    @Test
    void get_mapsFlagsAndTheLastQuizResult() {
        CourseModule module = module(1L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(module, progress(module, true, true, false), false)));
        when(quizResultRepository.findLastResultsPerModule(USER_ID))
                .thenReturn(List.of(new LastQuizRow(1L, 4, 10, NOW)));
        when(taskRepository.findByModuleIdInAndActiveTrueOrderByOrderNumberAscIdAsc(List.of(1L)))
                .thenReturn(List.of());

        ModuleProgress result = service.get(USER_ID).modules().get(0);

        assertThat(result.moduleId()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Module 1");
        assertThat(result.theoryCompleted()).isTrue();
        assertThat(result.quizCompleted()).isTrue();
        assertThat(result.codingCompleted()).isFalse();
        assertThat(result.lastQuiz().score()).isEqualTo(4);
        assertThat(result.lastQuiz().maxScore()).isEqualTo(10);
        assertThat(result.lastQuiz().completedAt()).isEqualTo(NOW);
    }

    @Test
    void get_moduleWithoutQuizAttemptHasNoLastQuiz() {
        CourseModule module = module(1L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(module, null, false)));

        ModuleProgress result = service.get(USER_ID).modules().get(0);

        assertThat(result.lastQuiz()).isNull();
        assertThat(result.theoryCompleted()).isFalse();
    }

    @Test
    void get_codingIsHiddenUntilTheTheoryIsRead_andNoTasksAreLoadedForIt() {
        CourseModule notRead = module(1L);
        CourseModule locked = module(2L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(notRead, progress(notRead, false, false, false), false),
                new ModuleAccessService.ModuleAccess(locked, null, true)));

        ProgressResponse result = service.get(USER_ID);

        assertThat(result.modules()).extracting(ModuleProgress::coding).containsOnlyNulls();
        verifyNoInteractions(taskRepository, codingResultRepository);
    }

    @Test
    void get_derivesTheStateOfEveryActiveTask() {
        CourseModule module = module(1L);
        CodingTask passed = task(11L, module, 1);
        CodingTask failed = task(12L, module, 2);
        CodingTask untouched = task(13L, module, 3);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(module, progress(module, true, false, false), false)));
        when(taskRepository.findByModuleIdInAndActiveTrueOrderByOrderNumberAscIdAsc(List.of(1L)))
                .thenReturn(List.of(passed, failed, untouched));
        when(codingResultRepository.summarizeByTask(USER_ID, List.of(11L, 12L, 13L), CodingResultStatus.PASSED))
                .thenReturn(List.of(
                        new TaskResultStats(11L, 2L, NOW),
                        new TaskResultStats(12L, 0L, NOW.minusSeconds(60))));

        var coding = service.get(USER_ID).modules().get(0).coding();

        assertThat(coding.activeTasks()).isEqualTo(3);
        assertThat(coding.passedTasks()).isEqualTo(1);
        assertThat(coding.tasks()).extracting(TaskProgress::taskId).containsExactly(11L, 12L, 13L);
        assertThat(coding.tasks()).extracting(TaskProgress::state)
                .containsExactly(TaskState.PASSED, TaskState.FAILED, TaskState.NOT_ATTEMPTED);
        assertThat(coding.tasks()).extracting(TaskProgress::lastAttemptAt)
                .containsExactly(NOW, NOW.minusSeconds(60), null);
    }

    @Test
    void get_moduleWithoutActiveTasksHasEmptyCoding() {
        CourseModule module = module(1L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(module, progress(module, true, false, false), false)));
        when(taskRepository.findByModuleIdInAndActiveTrueOrderByOrderNumberAscIdAsc(List.of(1L)))
                .thenReturn(List.of());

        var coding = service.get(USER_ID).modules().get(0).coding();

        assertThat(coding.activeTasks()).isZero();
        assertThat(coding.passedTasks()).isZero();
        assertThat(coding.tasks()).isEmpty();
        verifyNoInteractions(codingResultRepository);
    }

    private static CourseModule module(Long id) {
        CourseModule module = new CourseModule();
        module.setId(id);
        module.setTitle("Module " + id);
        module.setDescription("Description");
        module.setTheoryContent("# Theory");
        module.setOrderNumber(id.intValue());
        return module;
    }

    private static UserProgress progress(CourseModule module, boolean theory, boolean quiz, boolean coding) {
        UserProgress progress = new UserProgress();
        progress.setModule(module);
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        progress.setCodingCompleted(coding);
        return progress;
    }

    private static CodingTask task(Long id, CourseModule module, int order) {
        CodingTask task = new CodingTask();
        task.setId(id);
        task.setModule(module);
        task.setOrderNumber(order);
        task.setTitle("Task " + id);
        task.setActive(true);
        return task;
    }
}
