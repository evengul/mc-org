package app.mcorg.team.domain.exceptions;

public class NotFoundException extends DomainException {
    private NotFoundException(String code, Object... args) {
        super(code, args);
    }

    public static NotFoundException team(String id) {
        return new NotFoundException("api.error.team.not-found", id);
    }

    public static NotFoundException user(String username) {
        return new NotFoundException("api.error.user.not-found", username);
    }
}
