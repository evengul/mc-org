package app.mcorg.permission.presentation.rest.permission.mappers;

import app.mcorg.permission.domain.usecase.permission.GetUserPermissionsUseCase;
import app.mcorg.permission.presentation.rest.entities.UserPermissionsResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;

@UtilityClass
public class GetPermissionsMapper {
    public static ResponseEntity<UserPermissionsResponse> mapOut(GetUserPermissionsUseCase.OutputValues outputValues) {
        return ResponseEntity.ok(UserPermissionsResponse.from(outputValues.permissions()));
    }
}
