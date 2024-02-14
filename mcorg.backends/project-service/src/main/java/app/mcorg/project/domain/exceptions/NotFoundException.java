package app.mcorg.project.domain.exceptions;

public class NotFoundException extends DomainException {
    public NotFoundException(String notFound) {
        super("entity.notfound", notFound);
    }
}
