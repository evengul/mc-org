package app.mcorg.permission.presentation.rest.permission.mappers;

import app.mcorg.permission.domain.usecase.permission.RemoveAuthorityUseCase;
import app.mcorg.permission.presentation.rest.entities.RemovePermissionRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RemovePermissionMapper {
    public static RemoveAuthorityUseCase.InputValues mapIn(String username, RemovePermissionRequest request) {
        return new RemoveAuthorityUseCase.InputValues(username, request.level().getDomainValue(), request.id());
    }
}
