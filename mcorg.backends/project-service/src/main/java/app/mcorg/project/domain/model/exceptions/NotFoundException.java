package app.mcorg.project.domain.model.exceptions;

public class NotFoundException extends DomainException {
    private NotFoundException(String code, Object... args) {
        super(code, args);
    }

    public static NotFoundException task(String id) {
        return new NotFoundException("api.error.task.not-found", id);
    }

    public static NotFoundException project(String id) {
        return new NotFoundException("api.error.project.not-found", id);
    }

    public static NotFoundException user(String username) {
        return new NotFoundException("api.error.user.not-found", username);
    }
}
