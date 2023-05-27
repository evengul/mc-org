package app.mcorg.resources.infrastructure.repository.entities;

import app.mcorg.resources.domain.model.resource.ResourcePack;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ResourcePackMapper {
    public static ResourcePackEntity mapIn(ResourcePack resourcePack) {
        return new ResourcePackEntity(
                resourcePack.getId(),
                mapIn(resourcePack.getName()),
                mapIn(resourcePack.getVersion()),
                resourcePack.getServerType(),
                resourcePack.getResources()
        );
    }

    public static ResourcePack mapOut(ResourcePackEntity entity) {
        return ResourcePack.create(
                entity.getId(),
                mapOut(entity.getName()),
                mapOut(entity.getVersion()),
                entity.getServerType(),
                entity.getResources()
        );
    }

    public String mapIn(String s) {
        return s.replaceAll("\\.", "__");
    }

    private String mapOut(String s) {
        return s.replaceAll("__", ".");
    }
}
