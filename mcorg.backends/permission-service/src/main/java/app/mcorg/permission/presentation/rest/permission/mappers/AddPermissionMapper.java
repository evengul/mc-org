package app.mcorg.permission.presentation.rest.permission.mappers;

import app.mcorg.permission.domain.usecase.permission.AddAuthorityUseCase;
import app.mcorg.permission.presentation.rest.entities.AddPermissionRequest;

public class AddPermissionMapper {
    public static AddAuthorityUseCase.InputValues mapIn(String username, AddPermissionRequest request) {
        return new AddAuthorityUseCase.InputValues(
                username,
                request.level().getDomainValue(),
                request.id(),
                request.authority().getDomainValue()
        );
    }
}
