package app.mcorg.permission.presentation.rest.common.aspect;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.domain.api.PermissionService;
import app.mcorg.permission.domain.exceptions.AccessDeniedException;
import app.mcorg.permission.domain.exceptions.NotFoundException;
import app.mcorg.permission.domain.model.permission.Permission;
import app.mcorg.permission.domain.usecase.permission.GetUserPermissionsUseCase;
import app.mcorg.permission.infrastructure.repository.MongoUserPermissionsRepository;
import app.mcorg.permission.presentation.rest.entities.RemovePermissionRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;

@Aspect
@RequiredArgsConstructor
public class CanRemovePermissionAspect extends AbstractAspect<CanRemovePermission> {
    private final PermissionService permissionService;
    private final GetUserPermissionsUseCase getUserPermissionsUseCase;
    private final MongoUserPermissionsRepository repository;

    @Override
    public void check(JoinPoint joinPoint) {
        CanRemovePermission annotation = getAnnotation(joinPoint, CanRemovePermission.class);
        String username = getArg(joinPoint, annotation.usernameParameter(), String.class);
        RemovePermissionRequest request = getArg(joinPoint,
                annotation.requestParameter(),
                RemovePermissionRequest.class);
        Authority authority = getAuthority(username, request.level().getDomainValue(), request.id());

        boolean authorizedToRemove = permissionService.hasAuthority(request.level().getDomainValue(),
                request.id(),
                authority);

        boolean enoughToRemove = authority.equalsOrLower(Authority.PARTICIPANT) || otherAdminsAndOwners(
                username,
                request.level().getDomainValue(),
                request.id()) > 0;

        if (!authorizedToRemove || !enoughToRemove) {
            throw new AccessDeniedException();
        }
    }

    private long otherAdminsAndOwners(String username, AuthorityLevel level, String id) {
        return repository.findAllByPermissions_LevelAndPermissions_Id(level, id)
                .stream()
                .filter(permission -> !permission.getUsername().equals(username))
                .filter(permission -> permission.getPermissions().stream().anyMatch(
                        entity -> entity.id().equals(id) && entity.authority()
                                .equalsOrHigher(Authority.ADMIN)))
                .count();
    }

    private Authority getAuthority(String username, AuthorityLevel level, String id) {
        return getUserPermissionsUseCase.execute(new GetUserPermissionsUseCase.InputValues(username))
                .permissions()
                .getPermissions()
                .get(level)
                .stream()
                .filter(permission -> permission.id().equals(id))
                .findFirst()
                .map(Permission::authority)
                .orElseThrow(() -> NotFoundException.user(id));
    }
}
