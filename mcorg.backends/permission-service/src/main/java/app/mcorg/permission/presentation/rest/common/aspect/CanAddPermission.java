package app.mcorg.permission.presentation.rest.common.aspect;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CanAddPermission {
    String requestParameter() default "request";
}
