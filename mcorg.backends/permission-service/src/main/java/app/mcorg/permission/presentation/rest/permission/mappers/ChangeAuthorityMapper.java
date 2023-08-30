package app.mcorg.permission.presentation.rest.permission.mappers;

import app.mcorg.permission.domain.usecase.permission.ChangeAuthorityUseCase;
import app.mcorg.permission.presentation.rest.entities.ChangeAuthorityRequest;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ChangeAuthorityMapper {
    public static ChangeAuthorityUseCase.InputValues mapIn(String username, ChangeAuthorityRequest request) {
        return new ChangeAuthorityUseCase.InputValues(
                username,
                request.level().getDomainValue(),
                request.id(),
                request.authority().getDomainValue()
        );
    }
}
