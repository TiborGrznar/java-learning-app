package com.tgrznar.javalearningapp.user.teacher.dto;

import java.util.List;

/**
 * The result of a bulk change of the account state (UC-12). {@code changed} are the students whose state was
 * switched now, {@code unchanged} the students who already were in the requested state (the client shows a
 * notice for them). Both lists are in ascending id order and together contain every requested student once.
 */
public record StudentsActiveResponse(boolean active, List<Long> changed, List<Long> unchanged) {
}
