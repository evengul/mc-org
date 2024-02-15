package app.mcorg.project.domain.exceptions;

import lombok.Getter;

@Getter
public class DomainException extends RuntimeException {
    protected final Object[] params;

    public DomainException(String key, Object... args) {
        super(key);
        this.params = args;
    }
}
