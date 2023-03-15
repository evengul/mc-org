package app.mcorg.organizer.domain.model.exceptions;

import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

@UtilityClass
public class I18AbleExceptions {
    public static Supplier<NotFoundException> notFound(String notFound) {
        return () -> new NotFoundException(notFound);
    }
}
