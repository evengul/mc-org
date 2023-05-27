package app.mcorg.resources.domain.api;

import app.mcorg.resources.domain.model.resource.ResourcePack;

import java.util.List;
import java.util.Optional;

public interface Resources {

    Optional<ResourcePack> getResourcePack(String id);

    List<ResourcePack> getResourcePacks();

    List<ResourcePack> getResourcePacks(String version);

    ResourcePack persist(ResourcePack resourcePack);
}
