package app.mcorg.project.presentation.rest.common.advice;

import app.mcorg.project.presentation.rest.entities.errors.ErrorMessageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.UncategorizedMongoDbException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandlerControllerAdvice {

    @ExceptionHandler(UncategorizedMongoDbException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorMessageResponse<String> unknownGeneralException(UncategorizedMongoDbException e) {
        UUID id = UUID.randomUUID();
        log.error("Received unknown error with id {}", id, e);
        return ErrorMessageResponse.unknown(String.format("Unknown database error occurred. Check the logs of this id for more information [%s]", id));
    }
}
