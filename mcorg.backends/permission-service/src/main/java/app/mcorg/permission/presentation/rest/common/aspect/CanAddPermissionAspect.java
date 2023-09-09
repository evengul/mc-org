package app.mcorg.permission.presentation.rest.common.aspect;

import app.mcorg.permission.domain.api.PermissionService;
import app.mcorg.permission.domain.exceptions.AccessDeniedException;
import app.mcorg.permission.presentation.rest.entities.AddPermissionRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class CanAddPermissionAspect extends AbstractAspect<CanAddPermission> {

    private final PermissionService permissionService;

    @Override
    public void check(JoinPoint joinPoint) {
        CanAddPermission annotation = getAnnotation(joinPoint, CanAddPermission.class);
        AddPermissionRequest request = getArg(joinPoint, annotation.requestParameter(), AddPermissionRequest.class);

        boolean canAdd = permissionService.hasAuthority(request.level().getDomainValue(),
                                                        request.id(),
                                                        request.authority().getDomainValue());

        if (!canAdd) {
            throw new AccessDeniedException();
        }

    }
}
