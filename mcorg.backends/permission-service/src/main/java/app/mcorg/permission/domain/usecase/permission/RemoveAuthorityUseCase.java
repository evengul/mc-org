package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;

public class RemoveAuthorityUseCase
        extends EditPermissionsUseCase<RemoveAuthorityUseCase.InputValues> {
    public RemoveAuthorityUseCase(GetUserPermissionsUseCase useCase,
                                  UnitOfWork<UserPermissions> unitOfWork) {
        super(useCase, unitOfWork);
    }

    @Override
    protected void edit(InputValues input, UserPermissions permissions) {
        permissions.removeAuthority(input.level, input.id);
    }

    public record InputValues(String username,
                              AuthorityLevel level,
                              String id) implements EditPermissionsUseCase.InputValues {
    }
}
