package app.mcorg.server.core.model.exceptions;

public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Object... values) {
        super(String.format(message, values));
    }
}
