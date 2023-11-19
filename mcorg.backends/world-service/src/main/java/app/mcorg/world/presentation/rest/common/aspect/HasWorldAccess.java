package app.mcorg.world.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;

public @interface HasWorldAccess {
    String value() default "id";

    Authority authority() default Authority.OWNER;
}
