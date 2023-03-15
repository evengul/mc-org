package app.mcorg.organizer.presentation.rest.common.advice;

import app.mcorg.organizer.domain.model.exceptions.ArchivedException;
import app.mcorg.organizer.domain.model.exceptions.I18AbleException;
import app.mcorg.organizer.domain.model.exceptions.NotFoundException;
import app.mcorg.organizer.domain.model.exceptions.UnconfirmedException;
import app.mcorg.organizer.presentation.rest.entities.errors.ErrorMessageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@RequiredArgsConstructor
public class CustomExceptionHandlingControllerAdvice {
    private final MessageSource messageSource;

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorMessageResponse<String> onNotFoundException(NotFoundException e) {
        return ErrorMessageResponse.notFound(getMessage(e));
    }

    @ExceptionHandler(ArchivedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorMessageResponse<String> onArchivedException(ArchivedException e) {
        return ErrorMessageResponse.archived(getMessage(e));
    }

    @ExceptionHandler(UnconfirmedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ErrorMessageResponse<String> onUnconfirmedException(UnconfirmedException e) {
        return ErrorMessageResponse.invalidArgument(getMessage(e));
    }

    @ExceptionHandler(I18AbleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<String> unknownGeneralException(I18AbleException e) {
        return ErrorMessageResponse.unknown(getMessage(e));
    }

    private String getMessage(I18AbleException e) {
        return messageSource.getMessage(e.getMessage(), e.getParams(), LocaleContextHolder.getLocale());
    }
}
