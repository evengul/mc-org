package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;

public class AddAuthorityUseCase extends EditPermissionsUseCase<AddAuthorityUseCase.InputValues> {
    public AddAuthorityUseCase(GetUserPermissionsUseCase useCase,
                               UnitOfWork<UserPermissions> unitOfWork) {
        super(useCase, unitOfWork);
    }

    @Override
    protected void edit(InputValues input, UserPermissions permissions) {
        permissions.addAuthority(input.level, input.id, input.authority);
    }

    public record InputValues(String username,
                              AuthorityLevel level,
                              String id,
                              Authority authority) implements EditPermissionsUseCase.InputValues {
    }
}
