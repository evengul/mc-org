package app.mcorg.project.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;

public @interface HasProjectAccess {
    String value() default "id";

    Authority authority() default Authority.OWNER;
}
