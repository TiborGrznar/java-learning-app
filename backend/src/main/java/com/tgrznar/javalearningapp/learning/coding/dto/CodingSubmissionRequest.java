package com.tgrznar.javalearningapp.learning.coding.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The student's solution. The limit is in characters and leaves room for JSON escaping inside the
 * default request body limit (16 KiB); an extreme body is refused earlier with PAYLOAD_TOO_LARGE.
 */
public record CodingSubmissionRequest(
        @NotBlank(message = "Kód nesmie byť prázdny")
        @Size(max = CodingSubmissionRequest.MAX_CODE_LENGTH, message = "Kód môže mať najviac 12000 znakov")
        String code) {

    public static final int MAX_CODE_LENGTH = 12_000;

    /** Keeps the student's code out of logs. */
    @Override
    public String toString() {
        return "CodingSubmissionRequest[code=" + (code == null ? 0 : code.length()) + " characters]";
    }
}
