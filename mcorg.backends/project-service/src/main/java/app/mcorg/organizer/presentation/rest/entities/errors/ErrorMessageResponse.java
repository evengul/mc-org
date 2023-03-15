package app.mcorg.organizer.presentation.rest.entities.errors;

import org.springframework.lang.NonNull;

public record ErrorMessageResponse<M>(@NonNull ErrorType errorType, @NonNull M message) {
    public static <T>ErrorMessageResponse<T> validationError(T message) {
        return new ErrorMessageResponse<>(ErrorType.VALIDATION, message);
    }
    public static <T>ErrorMessageResponse<T> missingArgument(T message) {
        return new ErrorMessageResponse<>(ErrorType.ARGUMENT_MISSING, message);
    }
    public static <T>ErrorMessageResponse<T> invalidArgument(T message) {
        return new ErrorMessageResponse<>(ErrorType.INVALID_ARGUMENT, message);
    }
    public static <T>ErrorMessageResponse<T> notFound(T message) {
        return new ErrorMessageResponse<>(ErrorType.NOT_FOUND, message);
    }
    public static <T>ErrorMessageResponse<T> archived(T message) {
        return new ErrorMessageResponse<>(ErrorType.ARCHIVED_PROJECT, message);
    }
    public static <T>ErrorMessageResponse<T> unknown(T message) {
        return new ErrorMessageResponse<>(ErrorType.UNKNOWN, message);
    }
}
