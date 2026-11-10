package com.tgrznar.javalearningapp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A password that BCrypt can hash in full: at least 8 characters and at most 72 <b>bytes</b> in UTF-8.
 * BCrypt ignores everything beyond 72 bytes, and a character with a diacritic takes two bytes or more,
 * so a limit counted in characters would let a long password be silently cut. A null value is valid here,
 * a missing password is the job of {@code @NotBlank}.
 */
@Documented
@Constraint(validatedBy = ValidPasswordValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE,
        ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "Heslo musí mať aspoň 8 znakov a najviac 72 bajtov (znaky s diakritikou zaberajú viac bajtov)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
