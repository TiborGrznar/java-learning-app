package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.ChangePasswordRequest;
import com.tgrznar.javalearningapp.auth.dto.LoginRequest;
import com.tgrznar.javalearningapp.auth.dto.RefreshRequest;
import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidAccessTokenException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCredentialsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCurrentPasswordException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import com.tgrznar.javalearningapp.auth.exception.PasswordUnchangedException;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.common.validation.ValidPasswordValidator;
import com.tgrznar.javalearningapp.ratelimit.LimiterKeys;
import com.tgrznar.javalearningapp.ratelimit.RateLimiter;
import com.tgrznar.javalearningapp.ratelimit.RateLimiters;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

import java.util.Locale;

/** Registration, login, token refresh and logout (UC-01, UC-02). */
@Slf4j
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final RateLimiter loginFailures;
    private final RateLimiter passwordChangeFailures;

    // Compared against when the e-mail is unknown, so that response time does not reveal
    // whether an account exists.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService,
                       RateLimiters rateLimiters) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.loginFailures = rateLimiters.loginFailuresPerEmail();
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
        this.passwordChangeFailures = rateLimiters.passwordChangeFailuresPerUser();
    }

    /** Self-service registration. The role is always STUDENT, the client cannot choose it. */
    @Transactional
    public void register(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordMismatchException();
        }

        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        User user = new User();
        user.setName(request.name().trim());
        user.setSurname(request.surname().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(UserRole.STUDENT);

        try {
            // Flush now so a concurrent duplicate is caught here, not at commit time.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException();
        }
    }

    /**
     * Failed logins are counted per e-mail whatever the IP, and counted the same way for an unknown
     * e-mail, so the limit reveals nothing about which accounts exist. The key is a hash of the e-mail.
     * Known trade-off: anyone can block logins to a known e-mail for the length of the window; only
     * failures count and a successful login clears the counter, so the block is short and never permanent.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        String limiterKey = LimiterKeys.hashed(email);
        loginFailures.check(limiterKey);

        User user = userRepository.findByEmail(email).orElse(null);

        String hashToCheck = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        // Same error for unknown e-mail and wrong password (prevents user enumeration).
        if (user == null || !passwordMatches) {
            loginFailures.record(limiterKey);
            throw new InvalidCredentialsException();
        }
        // Reported only after the password was verified.
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        loginFailures.clear(limiterKey);
        return buildResponse(user, refreshTokenService.issue(user));
    }

    /** Exchanges a valid refresh token for a new access and refresh token pair (rotation). */
    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(request.refreshToken());

        if (!rotated.user().isActive()) {
            // Throwing rolls the transaction back, so the old token stays as it was.
            throw new AccountDisabledException();
        }

        return buildResponse(rotated.user(), rotated.refreshToken());
    }

    @Transactional
    public void logout(RefreshRequest request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    /**
     * Changes the password of the logged-in user. The id comes from the verified token, never from the request.
     * Wrong current passwords are counted per user, so a stolen access token cannot be used to guess the
     * password; a successful change clears the counter. Every refresh token of the user is revoked, so the other
     * devices have to log in again, and the caller gets a fresh token pair and stays logged in. Access tokens
     * that were already issued stay valid until they expire.
     */
    @Transactional
    public AuthResponse changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmNewPassword())) {
            throw new PasswordMismatchException();
        }

        // Unlike the JWT filter this reads the database, so a deleted or deactivated account is refused.
        User user = userRepository.findById(userId).orElseThrow(InvalidAccessTokenException::new);
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        String limiterKey = String.valueOf(userId);
        passwordChangeFailures.check(limiterKey);

        if (!currentPasswordMatches(request.currentPassword(), user)) {
            passwordChangeFailures.record(limiterKey);
            throw new InvalidCurrentPasswordException();
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new PasswordUnchangedException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.saveAndFlush(user);
        refreshTokenService.revokeAllForUser(userId);
        passwordChangeFailures.clear(limiterKey);

        // Never log the password.
        log.info("Password changed: userId={}", userId);
        return buildResponse(user, refreshTokenService.issue(user));
    }

    /** A password longer than BCrypt can hash cannot be the current one, so the encoder is not asked about it. */
    private boolean currentPasswordMatches(String candidate, User user) {
        if (candidate.getBytes(StandardCharsets.UTF_8).length > ValidPasswordValidator.MAX_BYTES) {
            return false;
        }
        return passwordEncoder.matches(candidate, user.getPasswordHash());
    }

    private AuthResponse buildResponse(User user, String refreshToken) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                refreshToken,
                TOKEN_TYPE,
                jwtService.getAccessTokenTtlSeconds(),
                user.getRole());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}