package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.learning.ModuleAccessService;
import com.tgrznar.javalearningapp.learning.ModuleProgressService;
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
import com.tgrznar.javalearningapp.quizquestion.QuizOption;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestion;
import com.tgrznar.javalearningapp.quizquestion.QuizQuestionRepository;
import com.tgrznar.javalearningapp.ratelimit.RateLimiters;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * Loading and submitting a module quiz (UC-06, UC-07). A quiz is drawn at random from the module's
 * question bank and the order of the questions and of the options is shuffled on every load.
 * <p>
 * A quiz token works for one submission only: its id is stored with the result under a unique key,
 * so a second submission (a replay, or two requests at the same moment) is rejected by the database
 * with 409 QUIZ_ALREADY_SUBMITTED, and the student has to load a new quiz. After a failed attempt the
 * response shows which answers were wrong but not the correct options, these come with a pass.
 * <p>
 * Deliberately not @Transactional: creating the progress row relies on running outside a transaction,
 * the result and the quizCompleted flag are written together in QuizAttemptRecorder.
 */
@Service
@RequiredArgsConstructor
public class StudentQuizService {

    /** Name of the unique key on quiz_results.quiz_token_id, see V6. */
    private static final String TOKEN_KEY_NAME = "uq_quiz_results_quiz_token_id";

    private final ModuleAccessService accessService;
    private final ModuleProgressService progressService;
    private final QuizAttemptRecorder attemptRecorder;
    private final QuizQuestionRepository questionRepository;
    private final QuizTokenService tokenService;
    private final QuizProperties properties;
    private final RandomGenerator random;
    private final RateLimiters rateLimiters;

    public QuizResponse load(Long userId, Long moduleId) {
        // Every load draws a new quiz and a new token, so repeated loads are limited per student.
        rateLimiters.quizLoadPerUser().checkAndRecord(String.valueOf(userId));
        requireQuizAccess(userId, moduleId);

        List<QuizQuestion> bank = questionRepository.findByModuleIdOrderByIdAsc(moduleId);
        if (bank.isEmpty()) {
            throw new QuizNotAvailableException(moduleId);
        }

        List<QuizQuestion> shuffled = new ArrayList<>(bank);
        Collections.shuffle(shuffled, random);
        List<QuizQuestion> drawn = shuffled.stream().limit(properties.questionsPerAttempt()).toList();

        List<Long> drawnIds = drawn.stream().map(QuizQuestion::getId).toList();
        return new QuizResponse(
                tokenService.issue(userId, moduleId, drawnIds),
                drawn.stream().map(this::toResponse).toList());
    }

    public QuizResultResponse submit(Long userId, Long moduleId, QuizSubmissionRequest request) {
        ModuleAccessService.ModuleAccess access = requireQuizAccess(userId, moduleId);

        VerifiedQuizToken token = tokenService.verify(request.quizToken(), userId, moduleId);
        List<Long> drawnIds = token.questionIds();
        if (drawnIds.isEmpty()) {
            throw new InvalidQuizTokenException();
        }

        Map<Long, QuizQuestion> bank = questionRepository.findByModuleIdOrderByIdAsc(moduleId).stream()
                .collect(Collectors.toMap(QuizQuestion::getId, Function.identity()));
        if (!bank.keySet().containsAll(drawnIds)) {
            throw new QuizOutdatedException("A question of the quiz no longer exists");
        }

        Map<Long, QuizOption> selected = answersByQuestion(request.answers(), drawnIds);

        int score = 0;
        List<QuizAnswerResult> results = new ArrayList<>(drawnIds.size());
        for (Long questionId : drawnIds) {
            QuizOption correctOption = bank.get(questionId).getCorrectOption();
            QuizOption chosen = selected.get(questionId);
            boolean correct = correctOption == chosen;
            if (correct) {
                score++;
            }
            results.add(new QuizAnswerResult(questionId, chosen, correctOption, correct));
        }

        int maxScore = drawnIds.size();
        // Whole numbers only, so that the threshold is exact, for example 7 of 10 at 70 percent.
        boolean passed = score * 100 >= properties.passThresholdPercent() * maxScore;

        progressService.ensureProgress(userId, access);
        // The result is built only after the token was used up, so a rejected submission shows nothing.
        recordAttempt(userId, moduleId, token.id(), score, maxScore, passed);

        List<QuizAnswerResult> shown = passed
                ? results
                : results.stream().map(QuizAnswerResult::withoutCorrectOption).toList();
        return new QuizResultResponse(score, maxScore, score * 100 / maxScore, passed, shown);
    }

    /**
     * Writes the attempt together with the token id. The unique key on the token id is the single point
     * that decides which of two requests with the same token wins, it works even when both arrive at once.
     */
    private void recordAttempt(Long userId, Long moduleId, String tokenId, int score, int maxScore, boolean passed) {
        try {
            attemptRecorder.record(userId, moduleId, tokenId, score, maxScore, passed);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateTokenViolation(e)) {
                throw new QuizAlreadySubmittedException();
            }
            throw e;
        }
    }

    /** True when the unique key on the token id rejected the insert. Any other integrity error is a real fault. */
    static boolean isDuplicateTokenViolation(Throwable error) {
        String message = NestedExceptionUtils.getMostSpecificCause(error).getMessage();
        return message != null && message.contains(TOKEN_KEY_NAME);
    }

    /** The module must be unlocked and its theory read before the quiz can be loaded or submitted. */
    private ModuleAccessService.ModuleAccess requireQuizAccess(Long userId, Long moduleId) {
        ModuleAccessService.ModuleAccess access = accessService.requireUnlocked(userId, moduleId);
        if (access.progress() == null || !access.progress().isTheoryCompleted()) {
            throw new TheoryNotCompletedException(moduleId);
        }
        return access;
    }

    /** The options are shuffled but each keeps its key, so the answer is matched by key, not by position. */
    private QuizQuestionResponse toResponse(QuizQuestion question) {
        List<QuizOptionResponse> options = new ArrayList<>(List.of(
                new QuizOptionResponse(QuizOption.A, question.getOptionA()),
                new QuizOptionResponse(QuizOption.B, question.getOptionB()),
                new QuizOptionResponse(QuizOption.C, question.getOptionC()),
                new QuizOptionResponse(QuizOption.D, question.getOptionD())));
        Collections.shuffle(options, random);
        return new QuizQuestionResponse(question.getId(), question.getQuestionText(), options);
    }

    /** The answers must cover exactly the drawn questions, each once. */
    private Map<Long, QuizOption> answersByQuestion(List<QuizAnswerRequest> answers, List<Long> drawnIds) {
        Map<Long, QuizOption> selected = new HashMap<>();
        for (QuizAnswerRequest answer : answers) {
            if (selected.put(answer.questionId(), answer.option()) != null) {
                throw new InvalidQuizAnswersException("Duplicate answer for question " + answer.questionId());
            }
        }
        if (!selected.keySet().equals(new HashSet<>(drawnIds))) {
            throw new InvalidQuizAnswersException("Answers do not match the questions of the quiz");
        }
        return selected;
    }
}