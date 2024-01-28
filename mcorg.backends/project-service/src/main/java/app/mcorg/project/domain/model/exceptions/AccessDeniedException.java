package app.mcorg.project.domain.model.exceptions;

public class AccessDeniedException extends DomainException {
    private AccessDeniedException(String key, Object... args) {
        super(key, args);
    }

    public static AccessDeniedException world(String id) {
        return new AccessDeniedException("api.error.world.not-permitted", id);
    }

    public static AccessDeniedException team(String id) {
        return new AccessDeniedException("api.error.team.not-permitted", id);
    }

    public static AccessDeniedException project(String id) {
        return new AccessDeniedException("api.error.project.not-permitted", id);
    }
}
