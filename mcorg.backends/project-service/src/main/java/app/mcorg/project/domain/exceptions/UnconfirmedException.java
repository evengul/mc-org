package app.mcorg.project.domain.exceptions;

public class UnconfirmedException extends DomainException {
    public UnconfirmedException(Object... args) {
        super("api.delete.unconfirmed", args);
    }
}
