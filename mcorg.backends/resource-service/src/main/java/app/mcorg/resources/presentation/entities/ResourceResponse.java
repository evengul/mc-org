package app.mcorg.resources.presentation.entities;

import app.mcorg.resources.domain.model.resource.Resource;
import org.springframework.lang.NonNull;

public record ResourceResponse(@NonNull String name, @NonNull Resource.Type type, @NonNull String url) {
    public static ResourceResponse from(Resource resource) {
        return new ResourceResponse(
                resource.getName(),
                resource.getType(),
                resource.getUrl()
        );
    }
}
