package app.mcorg.permission.domain.exceptions;

public class AccessDeniedException extends DomainException {
    public AccessDeniedException() {
        super("api.error.access-denied");
    }
}
