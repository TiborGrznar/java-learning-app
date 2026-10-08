package com.tgrznar.javalearningapp.user;

/**
 * Result of creating a user. The temporary password is returned exactly once and is never
 * stored in plain text, so the administrator has to hand it over to the new user.
 */
public record CreatedUserResponse(UserResponse user, String temporaryPassword) {

    // A record prints all fields; keep the password out of any accidental log line.
    @Override
    public String toString() {
        return "CreatedUserResponse[user=" + user + ", temporaryPassword=***]";
    }
}