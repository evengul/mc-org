package app.mcorg.project.domain.model.exceptions;

import lombok.Data;
import lombok.EqualsAndHashCode;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {
    protected final Object[] params;

    public DomainException(String key, Object... args) {
        super(key);
        this.params = args;
    }
}
