package com.gdghost.member;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/** Departments 목록에 있는 이름만. null은 통과(필수 여부는 @NotNull로) */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidDepartment.Validator.class)
public @interface ValidDepartment {

    String message() default "학과 목록에 있는 값이어야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<ValidDepartment, String> {

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || Departments.contains(value.strip());
        }

    }

}
