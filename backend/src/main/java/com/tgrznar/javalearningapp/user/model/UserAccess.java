package com.tgrznar.javalearningapp.user.model;

/**
 * What the security filter needs to know about a user on every request: the current role and whether the account
 * is active. Read from the database, so a deactivation or a role change takes effect at once, not when the access
 * token expires.
 */
public record UserAccess(UserRole role, boolean active) {
}
