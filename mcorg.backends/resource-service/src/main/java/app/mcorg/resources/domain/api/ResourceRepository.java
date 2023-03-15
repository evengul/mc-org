package app.mcorg.resources.domain.api;

import app.mcorg.resources.domain.model.resource.Resource;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository {

    Optional<Resource> getResource(String name);

    List<Resource> getResources();

    List<Resource> getResources(String version);
}
