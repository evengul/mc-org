package app.mcorg.team.presentation.rest.entities.errors;

import jakarta.validation.ConstraintViolation;
import org.springframework.lang.NonNull;
import org.springframework.validation.FieldError;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public record ValidationErrorResponse(@NonNull List<Validation> validationErrors) {
    public static ValidationErrorResponse from(Set<ConstraintViolation<?>> constraintViolations) {
        List<Validation> validationErrors = constraintViolations.stream()
                .map(violation -> new Validation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return new ValidationErrorResponse(validationErrors);
    }

    public static ValidationErrorResponse from(List<FieldError> fieldErrors) {
        List<Validation> validationErrors = fieldErrors.stream()
                .map(fieldError -> new Validation(fieldError.getField(), Optional.ofNullable(fieldError.getDefaultMessage()).orElse("Unknown validation error")))
                .toList();
        return new ValidationErrorResponse(validationErrors);
    }
}
