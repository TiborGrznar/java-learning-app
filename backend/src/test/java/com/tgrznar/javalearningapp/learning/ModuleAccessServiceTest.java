package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleAccessServiceTest {

    private static final Long USER_ID = 5L;
    private static final Sort COURSE_ORDER = Sort.by("orderNumber", "id");

    @Mock
    private CourseModuleRepository moduleRepository;

    @Mock
    private UserProgressRepository progressRepository;

    @InjectMocks
    private ModuleAccessService service;

    // ---------- accessFor ----------

    @Test
    void accessFor_noModules_returnsEmptyList() {
        givenCourse(List.of());

        assertThat(service.accessFor(USER_ID)).isEmpty();
    }

    @Test
    void accessFor_firstModuleIsUnlockedWithoutProgress() {
        givenCourse(List.of(module(1L)));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false);
    }

    @Test
    void accessFor_secondModuleIsLockedWithoutProgress() {
        givenCourse(List.of(module(1L), module(2L)));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, true);
    }

    @Test
    void accessFor_secondModuleUnlocksWhenPreviousQuizIsPassed() {
        CourseModule first = module(1L);
        givenCourse(List.of(first, module(2L)), progress(first, false, true, false));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, false);
    }

    @Test
    void accessFor_secondModuleStaysLockedWhenOnlyPreviousTheoryIsDone() {
        CourseModule first = module(1L);
        givenCourse(List.of(first, module(2L)), progress(first, true, false, true));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, true);
    }

    @Test
    void accessFor_thirdModuleStaysLockedWhenOnlyTheFirstQuizIsPassed() {
        CourseModule first = module(1L);
        givenCourse(List.of(first, module(2L), module(3L)), progress(first, false, true, false));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, false, true);
    }

    @Test
    void accessFor_moduleWithOwnProgressIsUnlockedEvenIfPreviousQuizIsNotPassed() {
        CourseModule third = module(3L);
        givenCourse(List.of(module(1L), module(2L), third), progress(third, true, false, false));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, true, false);
    }

    @Test
    void accessFor_progressRowWithAllFlagsOffDoesNotUnlock() {
        CourseModule second = module(2L);
        givenCourse(List.of(module(1L), second), progress(second, false, false, false));

        assertThat(service.accessFor(USER_ID))
                .extracting(ModuleAccessService.ModuleAccess::locked)
                .containsExactly(false, true);
    }

    // ---------- requireUnlocked ----------

    @Test
    void requireUnlocked_unlockedModule_returnsItWithProgress() {
        CourseModule first = module(1L);
        UserProgress progress = progress(first, true, false, false);
        givenCourse(List.of(first), progress);

        ModuleAccessService.ModuleAccess access = service.requireUnlocked(USER_ID, 1L);

        assertThat(access.module()).isSameAs(first);
        assertThat(access.progress()).isSameAs(progress);
        assertThat(access.locked()).isFalse();
    }

    @Test
    void requireUnlocked_lockedModule_throwsLocked() {
        givenCourse(List.of(module(1L), module(2L)));

        assertThatThrownBy(() -> service.requireUnlocked(USER_ID, 2L))
                .isInstanceOf(ModuleLockedException.class);
    }

    @Test
    void requireUnlocked_moduleOutsideTheCourse_throwsNotFound() {
        givenCourse(List.of(module(1L)));

        assertThatThrownBy(() -> service.requireUnlocked(USER_ID, 99L))
                .isInstanceOf(ModuleNotFoundException.class);
    }

    // ---------- helpers ----------

    private void givenCourse(List<CourseModule> modules, UserProgress... progress) {
        when(moduleRepository.findByActiveTrue(COURSE_ORDER)).thenReturn(modules);
        when(progressRepository.findByUserId(USER_ID)).thenReturn(List.of(progress));
    }

    private static CourseModule module(Long id) {
        CourseModule module = new CourseModule();
        module.setId(id);
        module.setTitle("Module " + id);
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