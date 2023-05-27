package app.mcorg.resources.presentation.mappers;

import app.mcorg.resources.domain.usecases.resource.GetAllResourcesUseCase;
import app.mcorg.resources.presentation.entities.ResourcePackResponse;
import app.mcorg.resources.presentation.entities.ResourcePacksResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;

@UtilityClass
public class GetAllResourcesOutputMapper {
    public static ResponseEntity<ResourcePacksResponse> map(GetAllResourcesUseCase.OutputValues output) {
        return ResponseEntity.ok(
                new ResourcePacksResponse(
                        output.resourcePacks()
                                .stream()
                                .map(ResourcePackResponse::from)
                                .toList()
                )
        );
    }
}
