package app.mcorg.permission.domain.exceptions;

public class NotFoundException extends DomainException {

    public NotFoundException(String resource, String id) {
        super(resource, id);
    }

    public static NotFoundException user(String id) {
        return new NotFoundException("resource.user", id);
    }
}
