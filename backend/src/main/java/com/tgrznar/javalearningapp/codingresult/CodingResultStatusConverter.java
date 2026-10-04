package com.tgrznar.javalearningapp.codingresult;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts CodingResultStatus to/from the lowercase snake_case string values
 * stored in the database ENUM column (e.g. "compile_error").
 */
@Converter(autoApply = true)
public class CodingResultStatusConverter implements AttributeConverter<CodingResultStatus, String> {

    @Override
    public String convertToDatabaseColumn(CodingResultStatus status) {
        return status == null ? null : status.name().toLowerCase();
    }

    @Override
    public CodingResultStatus convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : CodingResultStatus.valueOf(dbValue.toUpperCase());
    }
}