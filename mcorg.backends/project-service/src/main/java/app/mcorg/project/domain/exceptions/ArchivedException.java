package app.mcorg.project.domain.exceptions;

public class ArchivedException extends DomainException {
    public ArchivedException(String id) {
        super("project.archived", id);
    }
}
