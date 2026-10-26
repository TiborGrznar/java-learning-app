package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizTokenException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizOutdatedException;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
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

        assertThat(service.verify(token, USER_ID, MODULE_ID).questionIds()).containsExactly(3L, 1L, 2L);
    }

    @Test
    void verify_returnsTheTokenId_andTheSameTokenAlwaysGivesTheSameId() {
        String token = service.issue(USER_ID, MODULE_ID, List.of(1L));

        String first = service.verify(token, USER_ID, MODULE_ID).id();
        String second = service.verify(token, USER_ID, MODULE_ID).id();

        assertThat(first).isNotBlank().hasSizeLessThanOrEqualTo(36);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void issue_everyTokenGetsItsOwnId() {
        String first = service.issue(USER_ID, MODULE_ID, List.of(1L));
        String second = service.issue(USER_ID, MODULE_ID, List.of(1L));

        assertThat(service.verify(first, USER_ID, MODULE_ID).id())
                .isNotEqualTo(service.verify(second, USER_ID, MODULE_ID).id());
    }

    @Test
    void verify_validTokenWithoutAnId_throwsOutdated() {
        String token = tokenWithoutId();

        assertThatThrownBy(() -> service.verify(token, USER_ID, MODULE_ID))
                .isInstanceOf(QuizOutdatedException.class);
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

    /**
     * A correctly signed quiz token like the ones issued before tokens had an id. The key derivation
     * repeats the one in QuizTokenService on purpose: the test must not depend on the code it checks.
     */
    private static String tokenWithoutId() {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Decoders.BASE64.decode(JWT.secret()), "HmacSHA256"));
            var key = Keys.hmacShaKeyFor(mac.doFinal("quiz-token-v1".getBytes(StandardCharsets.UTF_8)));
            Instant now = Instant.now();
            return Jwts.builder()
                    .subject(String.valueOf(USER_ID))
                    .claim("module", MODULE_ID)
                    .claim("questions", List.of(1L))
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plus(Duration.ofHours(1))))
                    .signWith(key)
                    .compact();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
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