package com.tgrznar.javalearningapp.user.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts {@link UserRole} to/from the lowercase string values stored in the
 * database ENUM column ('student', 'teacher', 'admin'), keeping the Java enum
 * constants in standard UPPER_CASE style without relying on MySQL's
 * case-insensitive ENUM matching.
 * <p>
 * autoApply = true applies this converter to every UserRole field automatically,
 * without needing @Convert on each one.
 */
@Converter(autoApply = true)
public class UserRoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole role) {
        return role == null ? null : role.name().toLowerCase();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : UserRole.valueOf(dbValue.toUpperCase());
    }
}