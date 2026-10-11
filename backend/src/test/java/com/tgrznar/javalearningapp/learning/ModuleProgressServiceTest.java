package com.tgrznar.javalearningapp.learning;

import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.exception.ModuleNotFoundException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModuleProgressServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long MODULE_ID = 1L;

    @Mock
    private ModuleAccessService accessService;

    @Mock
    private UserProgressRepository progressRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ModuleProgressService service;

    @Test
    void markTheoryCompleted_withoutProgress_createsTheRowWithOnlyTheoryDone() {
        CourseModule module = module();
        User user = new User();
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module, null, false));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        service.markTheoryCompleted(USER_ID, MODULE_ID);

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(progressRepository).saveAndFlush(captor.capture());
        UserProgress saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getModule()).isSameAs(module);
        assertThat(saved.isTheoryCompleted()).isTrue();
        assertThat(saved.isQuizCompleted()).isFalse();
        assertThat(saved.isCodingCompleted()).isFalse();
        verify(progressRepository, never()).save(any(UserProgress.class));
    }

    @Test
    void markTheoryCompleted_withExistingProgress_keepsTheOtherFlags() {
        UserProgress progress = progress(false, true, false);
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), progress, false));

        service.markTheoryCompleted(USER_ID, MODULE_ID);

        verify(progressRepository).save(progress);
        assertThat(progress.isTheoryCompleted()).isTrue();
        assertThat(progress.isQuizCompleted()).isTrue();
        assertThat(progress.isCodingCompleted()).isFalse();
    }

    @Test
    void markTheoryCompleted_alreadyCompleted_writesNothing() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), progress(true, false, false), false));

        service.markTheoryCompleted(USER_ID, MODULE_ID);

        verifyNoInteractions(progressRepository, userRepository);
    }

    @Test
    void markTheoryCompleted_rowCreatedByConcurrentRequest_updatesThatRow() {
        UserProgress concurrent = progress(false, true, false);
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), null, false));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(progressRepository.saveAndFlush(any(UserProgress.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.of(concurrent));

        service.markTheoryCompleted(USER_ID, MODULE_ID);

        verify(progressRepository).save(concurrent);
        assertThat(concurrent.isTheoryCompleted()).isTrue();
        assertThat(concurrent.isQuizCompleted()).isTrue();
    }

    @Test
    void markTheoryCompleted_duplicateErrorWithoutAVisibleRow_isRethrown() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), null, false));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(progressRepository.saveAndFlush(any(UserProgress.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markTheoryCompleted(USER_ID, MODULE_ID))
                .isInstanceOf(DataIntegrityViolationException.class);

        verify(progressRepository, never()).save(any(UserProgress.class));
    }

    @Test
    void markTheoryCompleted_lockedModule_throwsAndWritesNothing() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID)).thenThrow(new ModuleLockedException(MODULE_ID));

        assertThatThrownBy(() -> service.markTheoryCompleted(USER_ID, MODULE_ID))
                .isInstanceOf(ModuleLockedException.class);

        verifyNoInteractions(progressRepository, userRepository);
    }

    @Test
    void markTheoryCompleted_unknownModule_throwsAndWritesNothing() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID)).thenThrow(new ModuleNotFoundException(MODULE_ID));

        assertThatThrownBy(() -> service.markTheoryCompleted(USER_ID, MODULE_ID))
                .isInstanceOf(ModuleNotFoundException.class);

        verifyNoInteractions(progressRepository, userRepository);
    }

    @Test
    void ensureProgress_existingProgress_isReturnedAsIs() {
        UserProgress existing = progress(false, true, false);

        UserProgress result = service.ensureProgress(USER_ID,
                new ModuleAccessService.ModuleAccess(module(), existing, false));

        assertThat(result).isSameAs(existing);
        verifyNoInteractions(progressRepository, userRepository);
    }

    @Test
    void ensureProgress_withoutProgress_createsAnEmptyRow() {
        CourseModule module = module();
        User user = new User();
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);

        UserProgress result = service.ensureProgress(USER_ID,
                new ModuleAccessService.ModuleAccess(module, null, false));

        ArgumentCaptor<UserProgress> captor = ArgumentCaptor.forClass(UserProgress.class);
        verify(progressRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue()).isSameAs(result);
        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getModule()).isSameAs(module);
        assertThat(result.isTheoryCompleted()).isFalse();
        assertThat(result.isQuizCompleted()).isFalse();
        assertThat(result.isCodingCompleted()).isFalse();
    }

    @Test
    void ensureProgress_rowCreatedByConcurrentRequest_returnsThatRow() {
        UserProgress concurrent = progress(true, false, false);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(new User());
        when(progressRepository.saveAndFlush(any(UserProgress.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.of(concurrent));

        UserProgress result = service.ensureProgress(USER_ID,
                new ModuleAccessService.ModuleAccess(module(), null, false));

        assertThat(result).isSameAs(concurrent);
    }

    private static CourseModule module() {
        CourseModule module = new CourseModule();
        module.setId(MODULE_ID);
        module.setTitle("Module");
        module.setOrderNumber(1);
        return module;
    }

    private static UserProgress progress(boolean theory, boolean quiz, boolean coding) {
        UserProgress progress = new UserProgress();
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        progress.setCodingCompleted(coding);
        return progress;
    }
}