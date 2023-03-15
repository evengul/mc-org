package app.mcorg.organizer.domain.model.exceptions;

public class UnconfirmedException extends I18AbleException {
    public UnconfirmedException(Object... args) {
        super("api.delete.unconfirmed", args);
    }
}
