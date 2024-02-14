package app.mcorg.project.domain.exceptions;

public class SchematicParseException extends DomainException {
    public SchematicParseException(Object... args) {
        super("file.parse", args);
    }

    @SuppressWarnings("unused")
    public String subKey(String childKey) {
        return String.format("%s.%s", this.getMessage(), childKey);
    }
}
