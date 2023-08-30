package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public abstract class EditPermissionsUseCase<I extends EditPermissionsUseCase.InputValues>
        extends UseCase<I, EditPermissionsUseCase.OutputValues> {

    private final GetUserPermissionsUseCase useCase;
    private final UnitOfWork<UserPermissions> unitOfWork;

    @Override
    public OutputValues execute(I input) {
        UserPermissions permissions = get(input.username());
        edit(input, permissions);
        return new OutputValues(unitOfWork.add(permissions));
    }

    protected abstract void edit(I input, UserPermissions permissions);

    private UserPermissions get(String username) {
        return useCase.execute(new GetUserPermissionsUseCase.InputValues(username))
                      .permissions();
    }

    public interface InputValues extends UseCase.InputValues {
        String username();
    }

    public record OutputValues(UserPermissions permissions) implements UseCase.OutputValues {
    }
}
