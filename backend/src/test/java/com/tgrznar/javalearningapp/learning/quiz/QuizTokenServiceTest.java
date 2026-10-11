package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizTokenException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizOutdatedException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizTokenServiceTest {

    private static final Long USER_ID = 5L;
    private static final Long MODULE_ID = 1L;
    private static final JwtProperties JWT = jwtProperties("0123456789abcdef0123456789abcdef");

    private final QuizTokenService service = new QuizTokenService(quizProperties(Duration.ofHours(2)), JWT);

    @Test
    void verify_returnsTheIssuedQuestionIdsInOrder() {
        String token = service.issue(USER_ID, MODULE_ID, List.of(3L, 1L, 2L));

        assertThat(service.verify(token, USER_ID, MODULE_ID)).containsExactly(3L, 1L, 2L);
    }

    @Test
    void verify_tokenOfAnotherStudent_throwsInvalid() {
        String token = service.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThatThrownBy(() -> service.verify(token, 6L, MODULE_ID))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void verify_tokenOfAnotherModule_throwsInvalid() {
        String token = service.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThatThrownBy(() -> service.verify(token, USER_ID, 2L))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void verify_tokenSignedWithAnotherSecret_throwsInvalid() {
        QuizTokenService other = new QuizTokenService(quizProperties(Duration.ofHours(2)),
                jwtProperties("fedcba9876543210fedcba9876543210"));
        String token = other.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThatThrownBy(() -> service.verify(token, USER_ID, MODULE_ID))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void verify_garbage_throwsInvalid() {
        assertThatThrownBy(() -> service.verify("not-a-token", USER_ID, MODULE_ID))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void verify_emptyToken_throwsInvalid() {
        assertThatThrownBy(() -> service.verify("", USER_ID, MODULE_ID))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void verify_expiredToken_throwsOutdated() {
        QuizTokenService shortLived = new QuizTokenService(quizProperties(Duration.ofSeconds(-10)), JWT);
        String token = shortLived.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThatThrownBy(() -> service.verify(token, USER_ID, MODULE_ID))
                .isInstanceOf(QuizOutdatedException.class);
    }

    @Test
    void verify_accessToken_isNotAcceptedAsQuizToken() {
        User user = new User();
        user.setId(USER_ID);
        user.setRole(UserRole.STUDENT);
        String accessToken = new JwtService(JWT).generateAccessToken(user);

        assertThatThrownBy(() -> service.verify(accessToken, USER_ID, MODULE_ID))
                .isInstanceOf(InvalidQuizTokenException.class);
    }

    @Test
    void issue_quizToken_isNotAcceptedAsAccessToken() {
        String quizToken = service.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThatThrownBy(() -> new JwtService(JWT).parseAccessToken(quizToken))
                .isInstanceOf(JwtException.class);
    }

    private static JwtProperties jwtProperties(String secret) {
        return new JwtProperties(
                Base64.getEncoder().encodeToString(secret.getBytes(StandardCharsets.UTF_8)),
                Duration.ofMinutes(15),
                Duration.ofDays(7));
    }

    private static QuizProperties quizProperties(Duration tokenTtl) {
        return new QuizProperties(70, 10, tokenTtl);
    }
}