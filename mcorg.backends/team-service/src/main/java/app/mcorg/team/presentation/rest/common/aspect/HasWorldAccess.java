package app.mcorg.team.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;

public @interface HasWorldAccess {
    String value() default "worldId";

    Authority authority() default Authority.OWNER;
}
