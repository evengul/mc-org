package app.mcorg.world.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.world.domain.api.PermissionService;
import app.mcorg.world.domain.exceptions.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class HasWorldAccessAspect extends AbstractAspect<HasWorldAccess> {

    private final PermissionService permissionService;

    @Override
    public void check(JoinPoint joinPoint) {
        HasWorldAccess annotation = getAnnotation(joinPoint, HasWorldAccess.class);
        String worldId = getArg(joinPoint, annotation.value(), String.class);
        Authority authority = annotation.authority();

        if (!permissionService.hasAuthority(worldId, authority)) {
            throw AccessDeniedException.world(worldId);
        }

    }
}
