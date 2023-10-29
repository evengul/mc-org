package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.function.Supplier;

@RequiredArgsConstructor
public class GetUserPermissionsUseCase
        extends UseCase<GetUserPermissionsUseCase.InputValues, GetUserPermissionsUseCase.OutputValues> {

    private final Permissions permissions;
    private final UnitOfWork<UserPermissions> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        UserPermissions userPermissions = permissions.get(input.username)
                .orElseGet(create(input.username));
        return new OutputValues(userPermissions);
    }

    private Supplier<UserPermissions> create(String username) {
        return () -> unitOfWork.add(UserPermissions.create(username));
    }

    public record InputValues(String username) implements UseCase.InputValues {
    }

    public record OutputValues(UserPermissions permissions) implements UseCase.OutputValues {
    }
}
