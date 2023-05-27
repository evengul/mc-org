package app.mcorg.resources.domain.model.resource;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.DomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Collections;
import java.util.List;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ResourcePack extends AggregateRoot<DomainEvent> {
    private final String id;
    private final String name;
    private final String version;
    private final ServerType serverType;
    private final List<Resource> resources;

    public static ResourcePack create(String name, String version, ServerType serverType) {
        return new ResourcePack(null, name, version, serverType, Collections.emptyList());
    }

    public static ResourcePack create(String id, String name, String version, ServerType serverType, List<Resource> resources) {
        return new ResourcePack(id, name, version, serverType, resources);
    }

    @SuppressWarnings("unused")
    public ResourcePack withNewVersion(String version) {
        return new ResourcePack(null, name, version, serverType, resources.stream()
                .map(resource -> Resource.create(resource.getName(), resource.getType()))
                .toList()
        );
    }

    public void addResource(String name, Resource.Type type, String url) {
        this.getResources().add(Resource.create(name, type, url));
    }


}
