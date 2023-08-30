package app.mcorg.permission.presentation.rest.entities;

import app.mcorg.common.domain.model.Authority;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthorityEntity {
    OWNER(Authority.OWNER),
    ADMIN(Authority.ADMIN),
    PARTICIPANT(Authority.PARTICIPANT);

    private final Authority domainValue;

    public static AuthorityEntity from(Authority authority) {
        return switch (authority) {
            case null -> null;
            case OWNER -> OWNER;
            case ADMIN -> ADMIN;
            case PARTICIPANT -> PARTICIPANT;
        };
    }
}
