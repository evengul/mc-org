package app.mcorg.organizer.domain.model.exceptions;

public class ArchivedException extends I18AbleException {
    public ArchivedException(String id) {
        super("project.archived", id);
    }
}
