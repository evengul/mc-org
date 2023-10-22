package app.mcorg.world.domain.exceptions;

public class NotFoundException extends DomainException {
    private NotFoundException(String code, Object... args) {
        super(code, args);
    }

    public static NotFoundException world(String id) {
        return new NotFoundException("api.error.world.not-found", id);
    }

    public static NotFoundException user(String username) {
        return new NotFoundException("api.error.user.not-found", username);
    }


}
