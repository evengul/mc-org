package app.mcorg.resources.presentation.mappers;

import app.mcorg.resources.domain.usecases.resource.GetResourcesForVersionUseCase;
import app.mcorg.resources.presentation.entities.ResourcesResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;

@UtilityClass
public class GetResourcesForVersionOutputMapper {
    public static ResponseEntity<ResourcesResponse> map(GetResourcesForVersionUseCase.OutputValues output) {
        return ResponseEntity.ok(ResourcesResponse.from(output.version(), output.resources()));
    }
}
