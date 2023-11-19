package app.mcorg.team.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;

public @interface HasTeamAccess {
    String value() default "id";
    
    Authority authority() default Authority.OWNER;
}
