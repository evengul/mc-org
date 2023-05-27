package app.mcorg.server.core.model.exceptions;

@SuppressWarnings("unused")
public class NotFoundException extends DomainException {
    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Object... values) {
        super(message, values);
    }
}
