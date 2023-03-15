package app.mcorg.resources.presentation.mappers;

import app.mcorg.resources.domain.usecases.resource.GetAllResourcesUseCase;
import app.mcorg.resources.presentation.entities.ResourceResponse;
import lombok.experimental.UtilityClass;
import org.springframework.http.ResponseEntity;

import java.util.List;

@UtilityClass
public class GetAllResourcesOutputMapper {
    public static ResponseEntity<List<ResourceResponse>> map(GetAllResourcesUseCase.OutputValues output) {
        return ResponseEntity.ok(
                output.resources()
                        .stream()
                        .map(ResourceResponse::from)
                        .toList()
        );
    }
}
