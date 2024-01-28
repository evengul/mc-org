package app.mcorg.project.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.project.domain.api.PermissionService;
import app.mcorg.project.domain.model.exceptions.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class HasProjectAccessAspect extends AbstractAspect<HasProjectAccess> {

    private final PermissionService permissionService;

    @Override
    public void check(JoinPoint joinPoint) {
        HasProjectAccess annotation = getAnnotation(joinPoint, HasProjectAccess.class);
        String projectId = getArg(joinPoint, annotation.value(), String.class);
        Authority authority = annotation.authority();

        if (!permissionService.hasAuthority(projectId, authority)) {
            throw AccessDeniedException.project(projectId);
        }

    }
}
