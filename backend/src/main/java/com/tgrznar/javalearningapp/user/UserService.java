package com.tgrznar.javalearningapp.user;

import com.tgrznar.javalearningapp.auth.exception.AccountDisabledException;
import com.tgrznar.javalearningapp.auth.exception.InvalidAccessTokenException;
import com.tgrznar.javalearningapp.user.dto.UserResponse;
import com.tgrznar.javalearningapp.user.model.User;
import com.tgrznar.javalearningapp.user.model.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** User profile operations. Grows with user management (UC-03, UC-04). */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Returns the profile of the user identified by a verified access token.
     * Unlike the JWT filter, this checks the database, so deleted or deactivated
     * accounts are rejected even while their access token is still valid.
     * The transaction is needed because UserResponse reads the lazy School association.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(InvalidAccessTokenException::new);

        if (!user.isActive()) {
            throw new AccountDisabledException();
        }

        return UserResponse.from(user);
    }
}