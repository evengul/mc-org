package app.mcorg.resources.presentation.mappers;

import app.mcorg.resources.domain.usecases.resource.StoreResourceUseCase;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

public class CreateResourcePackOutputMapper {
    public static ResponseEntity<Void> mapOut(HttpServletRequest request, StoreResourceUseCase.OutputValues outputValues) {
        URI location = ServletUriComponentsBuilder
                .fromContextPath(request)
                .path("/api/v1/resource-pack/{packId}")
                .buildAndExpand(outputValues.resourcePack().getId())
                .toUri();
        return ResponseEntity.created(location).build();
    }
}
