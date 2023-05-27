package app.mcorg.project.domain.model.exceptions;

public class SchematicParseException extends I18AbleException {
    public SchematicParseException(Object... args) {
        super("file.parse", args);
    }

    @SuppressWarnings("unused")
    public String subKey(String childKey) {
        return String.format("%s.%s", this.getMessage(), childKey);
    }
}
