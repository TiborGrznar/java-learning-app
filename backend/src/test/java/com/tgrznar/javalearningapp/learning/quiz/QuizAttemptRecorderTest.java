package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizresult.QuizResult;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizAttemptRecorderTest {

    private static final Long USER_ID = 5L;
    private static final Long MODULE_ID = 1L;

    @Mock
    private QuizResultRepository resultRepository;

    @Mock
    private UserProgressRepository progressRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseModuleRepository moduleRepository;

    @InjectMocks
    private QuizAttemptRecorder recorder;

    private final User user = new User();
    private final CourseModule module = new CourseModule();

    @BeforeEach
    void setUp() {
        when(userRepository.getReferenceById(USER_ID)).thenReturn(user);
        when(moduleRepository.getReferenceById(MODULE_ID)).thenReturn(module);
    }

    @Test
    void record_storesTheScoreWithTheUserAndTheModule() {
        recorder.record(USER_ID, MODULE_ID, 7, 10, false);

        ArgumentCaptor<QuizResult> captor = ArgumentCaptor.forClass(QuizResult.class);
        verify(resultRepository).saveAndFlush(captor.capture());
        QuizResult saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getModule()).isSameAs(module);
        assertThat(saved.getScore()).isEqualTo(7);
        assertThat(saved.getMaxScore()).isEqualTo(10);
        verifyNoInteractions(progressRepository);
    }

    @Test
    void record_passed_setsQuizCompletedAndKeepsTheOtherFlags() {
        UserProgress progress = progress(true, false);
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.of(progress));

        recorder.record(USER_ID, MODULE_ID, 8, 10, true);

        verify(progressRepository).save(progress);
        assertThat(progress.isQuizCompleted()).isTrue();
        assertThat(progress.isTheoryCompleted()).isTrue();
    }

    @Test
    void record_passedAgain_doesNotWriteTheProgressAgain() {
        UserProgress progress = progress(true, true);
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.of(progress));

        recorder.record(USER_ID, MODULE_ID, 9, 10, true);

        verify(progressRepository, never()).save(any(UserProgress.class));
    }

    @Test
    void record_failedAttempt_leavesAnEarlierPassUntouched() {
        UserProgress progress = progress(true, true);

        recorder.record(USER_ID, MODULE_ID, 2, 10, false);

        verifyNoInteractions(progressRepository);
        assertThat(progress.isQuizCompleted()).isTrue();
    }

    @Test
    void record_passedWithoutAProgressRow_throws() {
        when(progressRepository.findByUserIdAndModuleId(USER_ID, MODULE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.record(USER_ID, MODULE_ID, 8, 10, true))
                .isInstanceOf(IllegalStateException.class);
    }

    private static UserProgress progress(boolean theory, boolean quiz) {
        UserProgress progress = new UserProgress();
        progress.setTheoryCompleted(theory);
        progress.setQuizCompleted(quiz);
        return progress;
    }
}