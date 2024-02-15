package app.mcorg.project.domain.exceptions;

public class AccessDeniedException extends DomainException {
    private AccessDeniedException(String key, Object... args) {
        super(key, args);
    }

    public static AccessDeniedException project(String id) {
        return new AccessDeniedException("api.error.project.not-permitted", id);
    }
}
