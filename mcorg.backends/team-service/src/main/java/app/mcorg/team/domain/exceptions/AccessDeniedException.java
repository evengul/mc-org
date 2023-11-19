package app.mcorg.team.domain.exceptions;

public class AccessDeniedException extends DomainException {
    private AccessDeniedException(String key, Object... args) {
        super(key, args);
    }

    public static AccessDeniedException world(String id) {
        return new AccessDeniedException("api.error.world.not-found", id);
    }

    public static AccessDeniedException team(String id) {
        return new AccessDeniedException("api.error.team.not-found", id);
    }
}
