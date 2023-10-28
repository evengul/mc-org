package app.mcorg.permission.presentation.rest.permission;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.permission.domain.usecase.UseCaseExecutor;
import app.mcorg.permission.domain.usecase.permission.AddAuthorityUseCase;
import app.mcorg.permission.domain.usecase.permission.ChangeAuthorityUseCase;
import app.mcorg.permission.domain.usecase.permission.GetUserPermissionsUseCase;
import app.mcorg.permission.domain.usecase.permission.RemoveAuthorityUseCase;
import app.mcorg.permission.presentation.rest.entities.AddPermissionRequest;
import app.mcorg.permission.presentation.rest.entities.ChangeAuthorityRequest;
import app.mcorg.permission.presentation.rest.entities.RemovePermissionRequest;
import app.mcorg.permission.presentation.rest.entities.UserPermissionsResponse;
import app.mcorg.permission.presentation.rest.permission.mappers.AddPermissionMapper;
import app.mcorg.permission.presentation.rest.permission.mappers.ChangeAuthorityMapper;
import app.mcorg.permission.presentation.rest.permission.mappers.GetPermissionsMapper;
import app.mcorg.permission.presentation.rest.permission.mappers.RemovePermissionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class PermissionController implements PermissionResource {
    private final UseCaseExecutor executor;
    private final UsernameProvider usernameProvider;
    private final AddAuthorityUseCase addAuthorityUseCase;
    private final RemoveAuthorityUseCase removeAuthorityUseCase;
    private final ChangeAuthorityUseCase changeAuthorityUseCase;
    private final GetUserPermissionsUseCase getUserPermissionsUseCase;

    @Override
    public CompletableFuture<ResponseEntity<Void>> addPermission(String username, AddPermissionRequest request) {
        return executor.execute(
                addAuthorityUseCase,
                AddPermissionMapper.mapIn(username, request),
                outputValues -> ResponseEntity.ok(null));
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> removePermission(String username, RemovePermissionRequest request) {
        return executor.execute(removeAuthorityUseCase,
                RemovePermissionMapper.mapIn(username, request),
                outputValues -> ResponseEntity.ok(null));
    }

    @Override
    public CompletableFuture<ResponseEntity<Void>> changeAuthority(String username, ChangeAuthorityRequest request) {
        return executor.execute(changeAuthorityUseCase,
                ChangeAuthorityMapper.mapIn(username, request),
                outputValues -> ResponseEntity.ok(null));
    }

    @Override
    public CompletableFuture<ResponseEntity<UserPermissionsResponse>> getUserPermissions() {
        return executor.execute(getUserPermissionsUseCase,
                new GetUserPermissionsUseCase.InputValues(usernameProvider.get()),
                GetPermissionsMapper::mapOut);
    }
}
