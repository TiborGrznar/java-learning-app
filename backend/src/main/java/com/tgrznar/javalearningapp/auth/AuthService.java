package com.tgrznar.javalearningapp.auth;

import com.tgrznar.javalearningapp.auth.dto.AuthResponse;
import com.tgrznar.javalearningapp.auth.dto.LoginRequest;
import com.tgrznar.javalearningapp.auth.dto.RefreshRequest;
import com.tgrznar.javalearningapp.auth.dto.RegisterRequest;
import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.EmailAlreadyExistsException;
import com.tgrznar.javalearningapp.auth.exception.InvalidCredentialsException;
import com.tgrznar.javalearningapp.auth.exception.PasswordMismatchException;
import com.tgrznar.javalearningapp.auth.security.JwtService;
import com.tgrznar.javalearningapp.auth.token.RefreshTokenService;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import com.tgrznar.javalearningapp.user.model.UserRole;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** Registration, login, token refresh and logout (UC-01, UC-02). */
@Service
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // Compared against when the e-mail is unknown, so that response time does not reveal
    // whether an account exists.
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);

        String hashToCheck = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        // Same error for unknown e-mail and wrong password (prevents user enumeration).
        if (user == null || !passwordMatches) {
            throw new InvalidCredentialsException();
        }
        // Reported only after the password was verified.
        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

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