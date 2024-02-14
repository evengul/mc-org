package app.mcorg.project.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.project.domain.api.PermissionService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.access.AccessDeniedException;

@Aspect
@RequiredArgsConstructor
public class CheckAccessAspect extends AbstractAspect<CheckAccess> {
    private final PermissionService permissionService;

    @Override
    public void check(JoinPoint joinPoint) {
        CheckAccess annotation = getAnnotation(joinPoint, CheckAccess.class);
        String id = getArg(joinPoint, annotation.value(), String.class);
        AuthorityLevel level = annotation.level();
        Authority authority = annotation.authority();

        boolean hasAccess = switch (level) {
            case WORLD -> permissionService.hasWorldAuthority(id, authority);
            case TEAM -> permissionService.hasTeamAuthority(id, authority);
            case PROJECT, TASK -> permissionService.hasProjectAuthority(id, authority);
        };

        if (!hasAccess) {
            throw new AccessDeniedException(
                    String.format("User does not have required access to [%s] with id [%s]", level, id)
            );
        }
    }
}
