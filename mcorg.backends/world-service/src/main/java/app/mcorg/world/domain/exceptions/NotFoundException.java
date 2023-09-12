package app.mcorg.world.domain.exceptions;

public class NotFoundException extends DomainException {
    public NotFoundException(String notFound) {
        super("entity.notfound", notFound);
    }
}
