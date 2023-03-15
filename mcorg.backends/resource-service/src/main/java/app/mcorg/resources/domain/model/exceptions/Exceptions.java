package app.mcorg.resources.domain.model.exceptions;

import lombok.experimental.UtilityClass;

import java.util.function.Supplier;

@UtilityClass
public class Exceptions {
    public Supplier<NotFoundException> notFound(String resource) {
        return () -> new NotFoundException(String.format("Could not locate %s", resource));
    }
}
