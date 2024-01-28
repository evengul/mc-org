package app.mcorg.project.domain.model.exceptions;

public class UnconfirmedException extends DomainException {
    public UnconfirmedException(Object... args) {
        super("api.delete.unconfirmed", args);
    }
}
