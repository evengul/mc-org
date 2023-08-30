package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DeleteUserUseCase extends UseCase<DeleteUserUseCase.InputValues, DeleteUserUseCase.OutputValues> {

    private final GetUserPermissionsUseCase useCase;
    private final UnitOfWork<UserPermissions> unitOfWork;

    @Override
    public OutputValues execute(InputValues input) {
        String permissionsId = get().getId();
        unitOfWork.remove(permissionsId);
        return new OutputValues();
    }

    private UserPermissions get() {
        return useCase.execute(new GetUserPermissionsUseCase.InputValues())
                      .permissions();
    }

    public record InputValues() implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}
