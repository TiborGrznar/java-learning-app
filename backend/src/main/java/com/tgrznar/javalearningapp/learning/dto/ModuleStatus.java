package com.tgrznar.javalearningapp.learning.dto;

import com.tgrznar.javalearningapp.userprogress.UserProgress;

/** How far a student got in a module, derived from the three completion flags. */
public enum ModuleStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED;

    /** No progress row means nothing was done yet; a row with all flags false is the same. */
    public static ModuleStatus of(UserProgress progress) {
        if (progress == null) {
            return NOT_STARTED;
        }
        return of(progress.isTheoryCompleted(), progress.isQuizCompleted(), progress.isCodingCompleted());
    }

    /** The same rule for callers that have only the three flags and no entity. */
    public static ModuleStatus of(boolean theory, boolean quiz, boolean coding) {
        if (theory && quiz && coding) {
            return COMPLETED;
        }
        if (theory || quiz || coding) {
            return IN_PROGRESS;
        }
        return NOT_STARTED;
    }
}