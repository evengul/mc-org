package app.mcorg.resources.presentation.entities;

import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.domain.model.resource.ServerType;
import org.springframework.lang.NonNull;

import java.util.List;

public record ResourcePackResponse(@NonNull String id,
                                   @NonNull String name,
                                   @NonNull String version,
                                   @NonNull ServerType serverType,
                                   @NonNull List<ResourceResponse> resources) {
    public static ResourcePackResponse from(ResourcePack pack) {
        return new ResourcePackResponse(
                pack.getId(),
                pack.getName(),
                pack.getVersion(),
                pack.getServerType(),
                pack.getResources().stream()
                        .map(ResourceResponse::from)
                        .toList()
        );
    }
}
