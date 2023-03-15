package app.mcorg.organizer.presentation.rest.common.aspect;

import java.lang.annotation.*;

@Inherited
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckArchived {
    String value() default "projectId";
}
