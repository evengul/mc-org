package app.mcorg.permission.presentation.rest.common.aspect;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanRemovePermission {
    String usernameParameter() default "username";

    String requestParameter() default "request";
}
