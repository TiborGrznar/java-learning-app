package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.module.CourseModuleRepository;
import com.tgrznar.javalearningapp.quizresult.QuizResult;
import com.tgrznar.javalearningapp.quizresult.QuizResultRepository;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.userprogress.UserProgress;
import com.tgrznar.javalearningapp.userprogress.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stores the result of a quiz attempt and, when the quiz was passed, the quizCompleted flag, in one
 * transaction so that no half-written state remains. It is a separate bean because a transactional
 * method called from inside its own class would not run in a transaction. The progress row has to
 * exist already, the caller creates it beforehand.
 */
@Service
@RequiredArgsConstructor
public class QuizAttemptRecorder {

    private final QuizResultRepository resultRepository;
    private final UserProgressRepository progressRepository;
    private final UserRepository userRepository;
    private final CourseModuleRepository moduleRepository;

    @Transactional
    public void record(Long userId, Long moduleId, int score, int maxScore, boolean passed) {
        QuizResult result = new QuizResult();
        result.setUser(userRepository.getReferenceById(userId));
        result.setModule(moduleRepository.getReferenceById(moduleId));
        result.setScore(score);
        result.setMaxScore(maxScore);
        resultRepository.saveAndFlush(result);

        if (passed) {
            UserProgress progress = progressRepository.findByUserIdAndModuleId(userId, moduleId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Progress row is missing for user " + userId + " in module " + moduleId));
            // Once passed, the flag stays set even when a later attempt fails.
            if (!progress.isQuizCompleted()) {
                progress.setQuizCompleted(true);
                progressRepository.save(progress);
            }
        }
    }
}