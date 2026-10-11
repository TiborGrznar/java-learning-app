package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.dto.ModuleStatus;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleDetail;
import com.tgrznar.javalearningapp.learning.dto.StudentModuleSummary;
import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentModuleServiceTest {

    private static final Long USER_ID = 5L;

    @Mock
    private ModuleAccessService accessService;

    @Mock
    private QuizQuestionRepository questionRepository;

    @InjectMocks
    private StudentModuleService service;

    // ---------- list ----------

    @Test
    void list_mapsEveryModuleWithItsStatusAndLockState() {
        CourseModule first = module(1L);
        when(accessService.accessFor(USER_ID)).thenReturn(List.of(
                new ModuleAccessService.ModuleAccess(first, progress(first, true, true, true), false),
                new ModuleAccessService.ModuleAccess(module(2L), null, true)));

        List<StudentModuleSummary> result = service.list(USER_ID);

        assertThat(result).extracting(StudentModuleSummary::id).containsExactly(1L, 2L);
        assertThat(result).extracting(StudentModuleSummary::status)
                .containsExactly(ModuleStatus.COMPLETED, ModuleStatus.NOT_STARTED);
        assertThat(result).extracting(StudentModuleSummary::locked).containsExactly(false, true);
    }

    @Test
    void list_noModules_returnsEmptyList() {
        when(accessService.accessFor(USER_ID)).thenReturn(List.of());

        assertThat(service.list(USER_ID)).isEmpty();
    }

    // ---------- get ----------

    @Test
    void get_returnsTheoryProgressAndQuestionCount() {
        CourseModule module = module(1L);
        when(accessService.requireUnlocked(USER_ID, 1L))
                .thenReturn(new ModuleAccessService.ModuleAccess(module, progress(module, true, false, false), false));
        when(questionRepository.countByModuleId(1L)).thenReturn(4L);

        StudentModuleDetail detail = service.get(USER_ID, 1L);

        assertThat(detail.id()).isEqualTo(1L);
        assertThat(detail.theoryContent()).isEqualTo("# Theory");
        assertThat(detail.status()).isEqualTo(ModuleStatus.IN_PROGRESS);
        assertThat(detail.theoryCompleted()).isTrue();
        assertThat(detail.quizCompleted()).isFalse();
        assertThat(detail.quizQuestionCount()).isEqualTo(4L);
    }

    @Test
    void get_withoutProgress_hasAllFlagsOff() {
        when(accessService.requireUnlocked(USER_ID, 1L))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(1L), null, false));
        when(questionRepository.countByModuleId(1L)).thenReturn(0L);

        StudentModuleDetail detail = service.get(USER_ID, 1L);

        assertThat(detail.status()).isEqualTo(ModuleStatus.NOT_STARTED);
        assertThat(detail.theoryCompleted()).isFalse();
        assertThat(detail.quizCompleted()).isFalse();
        assertThat(detail.codingCompleted()).isFalse();
    }

    @Test
    void get_lockedModule_throwsAndCountsNothing() {
        when(accessService.requireUnlocked(USER_ID, 2L)).thenThrow(new ModuleLockedException(2L));

        assertThatThrownBy(() -> service.get(USER_ID, 2L)).isInstanceOf(ModuleLockedException.class);

        verifyNoInteractions(questionRepository);
    }

    // ---------- helpers ----------

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
}