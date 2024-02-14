package app.mcorg.project.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckAccess {

    String value() default "id";

    AuthorityLevel level() default AuthorityLevel.PROJECT;

    Authority authority();

}
