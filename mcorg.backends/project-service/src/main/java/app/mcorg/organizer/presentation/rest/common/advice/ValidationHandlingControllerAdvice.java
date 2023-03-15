package app.mcorg.organizer.presentation.rest.common.advice;

import app.mcorg.organizer.presentation.rest.entities.errors.ErrorMessageResponse;
import app.mcorg.organizer.presentation.rest.entities.errors.ValidationErrorResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Optional;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ValidationHandlingControllerAdvice {
    private final MessageSource messageSource;

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<ValidationErrorResponse> onConstraintViolationException(ConstraintViolationException e) {
        ValidationErrorResponse response = ValidationErrorResponse.from(e.getConstraintViolations());
        return ErrorMessageResponse.validationError(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<ValidationErrorResponse> onMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        ValidationErrorResponse response = ValidationErrorResponse.from(e.getFieldErrors());
        return ErrorMessageResponse.validationError(response);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<String> onMissingParameterException(MissingServletRequestParameterException e) {
        String message = getMessage("api.param.missing", e.getParameterName());
        return ErrorMessageResponse.invalidArgument(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<String> onTypeMismatchException(MethodArgumentTypeMismatchException e) {
        String requiredType = Optional.ofNullable(e.getRequiredType()).map(Class::getName).orElse("UNKNOWN, SHOULD NOT OCCUR");
        String message = getMessage("api.param.type.mismatch", e.getParameter(), requiredType);
        return ErrorMessageResponse.invalidArgument(message);
    }

    @ExceptionHandler({Exception.class, RuntimeException.class})
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorMessageResponse<String> onDefaultException(Exception e) {
        String message = getMessage("api.unknown", e.getMessage());
        log.error("Received unknown error", e);
        return ErrorMessageResponse.unknown(message);
    }

    private String getMessage(String key, Object ...params) {
        return messageSource.getMessage(key, params, LocaleContextHolder.getLocale());
    }
}
