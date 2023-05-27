package app.mcorg.project.domain.model.exceptions;

public class NotFoundException extends I18AbleException {
    public NotFoundException(String notFound) {
        super("entity.notfound", notFound);
    }
}
