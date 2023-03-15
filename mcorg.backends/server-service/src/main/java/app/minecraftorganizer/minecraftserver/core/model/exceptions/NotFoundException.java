package app.minecraftorganizer.minecraftserver.core.model.exceptions;

public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Object... values) {
        super(message, values);
    }
}
