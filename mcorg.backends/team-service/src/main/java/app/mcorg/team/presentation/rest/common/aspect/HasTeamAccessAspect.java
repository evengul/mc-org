package app.mcorg.team.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.team.domain.api.PermissionService;
import app.mcorg.team.domain.exceptions.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class HasTeamAccessAspect extends AbstractAspect<HasTeamAccess> {

    private final PermissionService permissionService;

    @Override
    public void check(JoinPoint joinPoint) {
        HasTeamAccess annotation = getAnnotation(joinPoint, HasTeamAccess.class);
        String teamId = getArg(joinPoint, annotation.value(), String.class);
        Authority authority = annotation.authority();

        if (!permissionService.hasAuthority(teamId, authority)) {
            throw AccessDeniedException.team(teamId);
        }

    }
}
