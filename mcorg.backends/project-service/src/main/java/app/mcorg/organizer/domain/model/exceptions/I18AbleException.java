package app.mcorg.organizer.domain.model.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class I18AbleException extends RuntimeException{
    protected final Object[] params;

    public I18AbleException(String key, Object ...args) {
        super(key);
        this.params = args;
    }
}
