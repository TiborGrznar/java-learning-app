package com.tgrznar.javalearningapp.learning.quiz;

import com.tgrznar.javalearningapp.auth.security.JwtProperties;
import com.tgrznar.javalearningapp.learning.quiz.exception.InvalidQuizTokenException;
import com.tgrznar.javalearningapp.learning.quiz.exception.QuizOutdatedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies the token that ties a submitted quiz to the questions the student was shown.
 * It carries the student id, the module id and the ids of the drawn questions, so the server needs
 * no memory between loading and submitting a quiz and the student cannot pick other questions.
 * Every token also has a random id (jti). The id is stored with the quiz result under a unique key,
 * which is what makes a token usable for one submission only (see StudentQuizService).
 * The signing key is derived from the JWT secret with a fixed purpose label, so a quiz token is
 * never accepted as an access token and the other way round.
 */
@Service
public class QuizTokenService {

    private static final String KEY_PURPOSE = "quiz-token-v1";
    private static final String MODULE_CLAIM = "module";
    private static final String QUESTIONS_CLAIM = "questions";

    private final QuizProperties properties;
    private final SecretKey signingKey;
    private final JwtParser parser;

    public QuizTokenService(QuizProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.signingKey = deriveKey(jwtProperties);
        this.parser = Jwts.parser().verifyWith(signingKey).build();
    }

    public String issue(Long userId, Long moduleId, List<Long> questionIds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim(MODULE_CLAIM, moduleId)
                .claim(QUESTIONS_CLAIM, questionIds)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.tokenTtl())))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Returns the token id and the question ids in the token.
     *
     * @throws QuizOutdatedException     if the token has expired, or was issued before tokens had an id
     * @throws InvalidQuizTokenException if it is malformed, tampered with, or issued for another student or module
     */
    public VerifiedQuizToken verify(String token, Long userId, Long moduleId) {
        Claims claims;
        try {
            claims = parser.parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException e) {
            throw new QuizOutdatedException("Quiz token expired");
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidQuizTokenException();
        }

        Object module = claims.get(MODULE_CLAIM);
        Object questions = claims.get(QUESTIONS_CLAIM);
        if (!String.valueOf(userId).equals(claims.getSubject())
                || !(module instanceof Number moduleNumber) || moduleNumber.longValue() != moduleId
                || !(questions instanceof List<?> ids)) {
            throw new InvalidQuizTokenException();
        }

        String tokenId = claims.getId();
        if (tokenId == null || tokenId.isBlank()) {
            // Issued before tokens became single-use: the student just loads the quiz again.
            throw new QuizOutdatedException("Quiz token has no id");
        }
        return new VerifiedQuizToken(tokenId, ids.stream().map(id -> ((Number) id).longValue()).toList());
    }

    private static SecretKey deriveKey(JwtProperties jwtProperties) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(Decoders.BASE64.decode(jwtProperties.secret()), "HmacSHA256"));
            return Keys.hmacShaKeyFor(mac.doFinal(KEY_PURPOSE.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}