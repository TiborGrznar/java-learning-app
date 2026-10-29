package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.learning.ModuleAccessService;
import com.tgrznar.javalearningapp.learning.ModuleProgressService;
import com.tgrznar.javalearningapp.learning.exception.ModuleLockedException;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizAnswerRequest;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizAnswerResult;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizOptionResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizQuestionResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizResultResponse;
import com.tgrznar.javalearningapp.learning.quiz.dto.QuizSubmissionRequest;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizAnswersException;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizTokenException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizAlreadySubmittedException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizNotAvailableException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizOutdatedException;
import com.tgrznar.javalearningapp.learning.quiz.exception.TheoryNotCompletedException;
import com.tgrznar.javalearningapp.module.CourseModule;
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.ratelimit.TestRateLimiters;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.LongStream;
import java.sql.SQLIntegrityConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentQuizServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long MODULE_ID = 1L;
    private static final String TOKEN = "quiz-token";
    private static final String TOKEN_ID = "token-id-1";

    @Mock
    private ModuleAccessService accessService;

    @Mock
    private ModuleProgressService progressService;

    @Mock
    private QuizAttemptRecorder attemptRecorder;

    @Mock
    private QuizQuestionRepository questionRepository;

    @Mock
    private QuizTokenService tokenService;

    private StudentQuizService service;

    @BeforeEach
    void setUp() {
        service = new StudentQuizService(accessService, progressService, attemptRecorder, questionRepository,
                tokenService, new QuizProperties(70, 3, Duration.ofHours(2)), new Random(42), TestRateLimiters.unlimited());
    }

    // ---------- load ----------

    @Test
    void load_drawsTheConfiguredNumberOfDistinctQuestions() {
        givenQuizAccess();
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(5));
        when(tokenService.issue(eq(USER_ID), eq(MODULE_ID), anyList())).thenReturn(TOKEN);

        QuizResponse response = service.load(USER_ID, MODULE_ID);

        List<Long> shownIds = response.questions().stream().map(QuizQuestionResponse::id).toList();
        assertThat(response.quizToken()).isEqualTo(TOKEN);
        assertThat(shownIds).hasSize(3).doesNotHaveDuplicates().isSubsetOf(1L, 2L, 3L, 4L, 5L);
        verify(tokenService).issue(USER_ID, MODULE_ID, shownIds);
    }

    @Test
    void load_bankSmallerThanTheQuizSize_usesAllQuestions() {
        givenQuizAccess();
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(2));
        when(tokenService.issue(eq(USER_ID), eq(MODULE_ID), anyList())).thenReturn(TOKEN);

        QuizResponse response = service.load(USER_ID, MODULE_ID);

        assertThat(response.questions()).extracting(QuizQuestionResponse::id).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void load_everyQuestionKeepsItsOptionsPairedWithTheirKeys() {
        givenQuizAccess();
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(3));
        when(tokenService.issue(eq(USER_ID), eq(MODULE_ID), anyList())).thenReturn(TOKEN);

        QuizResponse response = service.load(USER_ID, MODULE_ID);

        for (QuizQuestionResponse question : response.questions()) {
            assertThat(question.options()).extracting(QuizOptionResponse::key)
                    .containsExactlyInAnyOrder(QuizOption.A, QuizOption.B, QuizOption.C, QuizOption.D);
            for (QuizOptionResponse option : question.options()) {
                assertThat(option.text()).isEqualTo(option.key().name() + question.id());
            }
        }
    }

    @Test
    void load_returnsAnotherOrderOnRepeatedLoads() {
        givenQuizAccess();
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(5));
        when(tokenService.issue(eq(USER_ID), eq(MODULE_ID), anyList())).thenReturn(TOKEN);

        Set<List<Long>> orders = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            orders.add(service.load(USER_ID, MODULE_ID).questions().stream()
                    .map(QuizQuestionResponse::id).toList());
        }

        assertThat(orders.size()).isGreaterThan(1);
    }

    @Test
    void load_withoutProgress_throwsTheoryNotCompleted() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), null, false));

        assertThatThrownBy(() -> service.load(USER_ID, MODULE_ID))
                .isInstanceOf(TheoryNotCompletedException.class);

        verifyNoInteractions(questionRepository, tokenService);
    }

    @Test
    void load_theoryNotRead_throwsTheoryNotCompleted() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), progress(false), false));

        assertThatThrownBy(() -> service.load(USER_ID, MODULE_ID))
                .isInstanceOf(TheoryNotCompletedException.class);

        verifyNoInteractions(questionRepository, tokenService);
    }

    @Test
    void load_moduleWithoutQuestions_throwsNotAvailable() {
        givenQuizAccess();
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(List.of());

        assertThatThrownBy(() -> service.load(USER_ID, MODULE_ID))
                .isInstanceOf(QuizNotAvailableException.class);

        verifyNoInteractions(tokenService);
    }

    @Test
    void load_lockedModule_throwsLocked() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID)).thenThrow(new ModuleLockedException(MODULE_ID));

        assertThatThrownBy(() -> service.load(USER_ID, MODULE_ID))
                .isInstanceOf(ModuleLockedException.class);

        verifyNoInteractions(questionRepository, tokenService);
    }

    // ---------- submit ----------

    @Test
    void submit_allCorrect_passes() {
        ModuleAccessService.ModuleAccess access = givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(3L, QuizOption.C)));

        assertThat(result.score()).isEqualTo(3);
        assertThat(result.maxScore()).isEqualTo(3);
        assertThat(result.percent()).isEqualTo(100);
        assertThat(result.passed()).isTrue();
        verify(progressService).ensureProgress(USER_ID, access);
        verify(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 3, 3, true);
    }

    @Test
    void submit_oneOfThree_failsAndRoundsThePercentDown() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.A), answer(3L, QuizOption.A)));

        assertThat(result.score()).isEqualTo(1);
        assertThat(result.percent()).isEqualTo(33);
        assertThat(result.passed()).isFalse();
        verify(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 1, 3, false);
    }

    @Test
    void submit_sevenOfTen_passesAtTheThreshold() {
        givenQuizAccess();
        givenToken(10);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(10));

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(answersWithCorrect(7, 10)));

        assertThat(result.score()).isEqualTo(7);
        assertThat(result.percent()).isEqualTo(70);
        assertThat(result.passed()).isTrue();
        verify(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 7, 10, true);
    }

    @Test
    void submit_sixOfTen_failsBelowTheThreshold() {
        givenQuizAccess();
        givenToken(10);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(10));

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(answersWithCorrect(6, 10)));

        assertThat(result.percent()).isEqualTo(60);
        assertThat(result.passed()).isFalse();
        verify(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 6, 10, false);
    }

    @Test
    void submit_passed_listsTheSelectedAndTheCorrectOption() {
        givenQuizAccess();
        givenToken(10);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(bank(10));

        // Question 1 answered wrong (D), the other nine right (A): 9 of 10 passes.
        QuizAnswerRequest[] answers = LongStream.rangeClosed(1, 10)
                .mapToObj(id -> answer(id, id == 1 ? QuizOption.D : QuizOption.A))
                .toArray(QuizAnswerRequest[]::new);

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(answers));

        assertThat(result.passed()).isTrue();
        QuizAnswerResult first = result.answers().get(0);
        assertThat(first.questionId()).isEqualTo(1L);
        assertThat(first.selected()).isEqualTo(QuizOption.D);
        assertThat(first.correctOption()).isEqualTo(QuizOption.A);
        assertThat(first.correct()).isFalse();
        assertThat(result.answers().get(1).correct()).isTrue();
        assertThat(result.answers().get(1).correctOption()).isEqualTo(QuizOption.A);
    }

    @Test
    void submit_failed_hidesTheCorrectOptionsButKeepsTheMarksAndTheSelection() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.A), answer(3L, QuizOption.A)));

        assertThat(result.passed()).isFalse();
        assertThat(result.answers()).extracting(QuizAnswerResult::correctOption).containsOnlyNulls();
        assertThat(result.answers()).extracting(QuizAnswerResult::correct).containsExactly(true, false, false);
        assertThat(result.answers()).extracting(QuizAnswerResult::selected).containsOnly(QuizOption.A);
    }

    @Test
    void submit_recordsTheTokenIdOfTheQuiz() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(3L, QuizOption.C)));

        verify(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 3, 3, true);
    }

    @Test
    void submit_answersInAnyOrder_areMatchedByQuestionId() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        QuizResultResponse result = service.submit(USER_ID, MODULE_ID, request(
                answer(3L, QuizOption.C), answer(1L, QuizOption.A), answer(2L, QuizOption.B)));

        assertThat(result.score()).isEqualTo(3);
        assertThat(result.answers()).extracting(QuizAnswerResult::questionId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void submit_theoryNotCompleted_throwsAndWritesNothing() {
        when(accessService.requireUnlocked(USER_ID, MODULE_ID))
                .thenReturn(new ModuleAccessService.ModuleAccess(module(), null, false));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(answer(1L, QuizOption.A))))
                .isInstanceOf(TheoryNotCompletedException.class);

        verifyNoInteractions(tokenService, questionRepository, progressService, attemptRecorder);
    }

    @Test
    void submit_expiredToken_throwsOutdatedAndWritesNothing() {
        givenQuizAccess();
        when(tokenService.verify(TOKEN, USER_ID, MODULE_ID)).thenThrow(new QuizOutdatedException("expired"));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(answer(1L, QuizOption.A))))
                .isInstanceOf(QuizOutdatedException.class);

        verifyNoInteractions(questionRepository, progressService, attemptRecorder);
    }

    @Test
    void submit_invalidToken_throwsAndWritesNothing() {
        givenQuizAccess();
        when(tokenService.verify(TOKEN, USER_ID, MODULE_ID)).thenThrow(new InvalidQuizTokenException());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(answer(1L, QuizOption.A))))
                .isInstanceOf(InvalidQuizTokenException.class);

        verifyNoInteractions(questionRepository, progressService, attemptRecorder);
    }

    @Test
    void submit_tokenWithoutQuestions_throwsInvalidToken() {
        givenQuizAccess();
        when(tokenService.verify(TOKEN, USER_ID, MODULE_ID)).thenReturn(new VerifiedQuizToken(TOKEN_ID, List.of()));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(answer(1L, QuizOption.A))))
                .isInstanceOf(InvalidQuizTokenException.class);

        verifyNoInteractions(progressService, attemptRecorder);
    }

    @Test
    void submit_questionDeletedAfterLoading_throwsOutdated() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID))
                .thenReturn(List.of(question(1L, QuizOption.A), question(2L, QuizOption.B)));

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(3L, QuizOption.C))))
                .isInstanceOf(QuizOutdatedException.class);

        verifyNoInteractions(progressService, attemptRecorder);
    }

    @Test
    void submit_answerForAQuestionOutsideTheQuiz_throwsInvalidAnswers() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(4L, QuizOption.C))))
                .isInstanceOf(InvalidQuizAnswersException.class);

        verifyNoInteractions(progressService, attemptRecorder);
    }

    @Test
    void submit_missingAnswer_throwsInvalidAnswers() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B))))
                .isInstanceOf(InvalidQuizAnswersException.class);

        verifyNoInteractions(progressService, attemptRecorder);
    }

    @Test
    void submit_duplicateAnswer_throwsInvalidAnswers() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(1L, QuizOption.A),
                answer(2L, QuizOption.B), answer(3L, QuizOption.C))))
                .isInstanceOf(InvalidQuizAnswersException.class);

        verifyNoInteractions(progressService, attemptRecorder);
    }

    @Test
    void submit_tokenAlreadyUsed_throwsAlreadySubmitted() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());
        doThrow(duplicateTokenViolation()).when(attemptRecorder)
                .record(USER_ID, MODULE_ID, TOKEN_ID, 3, 3, true);

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(3L, QuizOption.C))))
                .isInstanceOf(QuizAlreadySubmittedException.class);
    }

    @Test
    void submit_otherIntegrityViolation_isNotReportedAsAnAlreadySubmittedQuiz() {
        givenQuizAccess();
        givenToken(3);
        when(questionRepository.findByModuleIdOrderByIdAsc(MODULE_ID)).thenReturn(mixedBank());
        DataIntegrityViolationException foreignKeyFailure = new DataIntegrityViolationException("insert failed",
                new SQLIntegrityConstraintViolationException(
                        "Cannot add or update a child row: a foreign key constraint fails"));
        doThrow(foreignKeyFailure).when(attemptRecorder).record(USER_ID, MODULE_ID, TOKEN_ID, 3, 3, true);

        assertThatThrownBy(() -> service.submit(USER_ID, MODULE_ID, request(
                answer(1L, QuizOption.A), answer(2L, QuizOption.B), answer(3L, QuizOption.C))))
                .isSameAs(foreignKeyFailure);
    }

    // ---------- helpers ----------

    private ModuleAccessService.ModuleAccess givenQuizAccess() {
        ModuleAccessService.ModuleAccess access =
                new ModuleAccessService.ModuleAccess(module(), progress(true), false);
        when(accessService.requireUnlocked(USER_ID, MODULE_ID)).thenReturn(access);
        return access;
    }

    private void givenToken(int questionCount) {
        when(tokenService.verify(TOKEN, USER_ID, MODULE_ID)).thenReturn(new VerifiedQuizToken(
                TOKEN_ID, LongStream.rangeClosed(1, questionCount).boxed().toList()));
    }

    /** What MySQL reports when the unique key on quiz_results.quiz_token_id rejects a second insert. */
    private static DataIntegrityViolationException duplicateTokenViolation() {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry 'token-id-1' for key 'quiz_results.uq_quiz_results_quiz_token_id'"));
    }

    private static QuizSubmissionRequest request(QuizAnswerRequest... answers) {
        return new QuizSubmissionRequest(TOKEN, List.of(answers));
    }

    private static QuizAnswerRequest answer(Long questionId, QuizOption option) {
        return new QuizAnswerRequest(questionId, option);
    }

    private static QuizAnswerRequest[] answersWithCorrect(int correct, int total) {
        return LongStream.rangeClosed(1, total)
                .mapToObj(id -> answer(id, id <= correct ? QuizOption.A : QuizOption.B))
                .toArray(QuizAnswerRequest[]::new);
    }

    private static CourseModule module() {
        CourseModule module = new CourseModule();
        module.setId(MODULE_ID);
        module.setTitle("Module");
        module.setOrderNumber(1);
        return module;
    }

    private static UserProgress progress(boolean theory) {
        UserProgress progress = new UserProgress();
        progress.setTheoryCompleted(theory);
        return progress;
    }

    /** Questions 1 to size, all with the correct option A. */
    private static List<QuizQuestion> bank(int size) {
        return LongStream.rangeClosed(1, size).mapToObj(id -> question(id, QuizOption.A)).toList();
    }

    /** Three questions whose correct options are A, B and C. */
    private static List<QuizQuestion> mixedBank() {
        return List.of(question(1L, QuizOption.A), question(2L, QuizOption.B), question(3L, QuizOption.C));
    }

    private static QuizQuestion question(Long id, QuizOption correct) {
        QuizQuestion question = new QuizQuestion();
        question.setId(id);
        question.setQuestionText("Question " + id);
        question.setOptionA("A" + id);
        question.setOptionB("B" + id);
        question.setOptionC("C" + id);
        question.setOptionD("D" + id);
        question.setCorrectOption(correct);
        return question;
    }
}