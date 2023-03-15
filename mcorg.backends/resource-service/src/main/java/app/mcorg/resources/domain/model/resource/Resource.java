package app.mcorg.resources.domain.model.resource;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.DomainEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Resource extends AggregateRoot<DomainEvent> {

    private final UUID id;
    private final String name;
    private final Type type;
    private final Map<String, String> urls;

    public static Resource newInstance(String name, Type type) {
        return new Resource(UUID.randomUUID(), name, type, new HashMap<>());
    }

    public void addVersionUrl(String version, String url) {
        this.urls.put(version, url);
    }

    @RequiredArgsConstructor
    @Getter
    public enum Type {
        MOD("/mods"),
        RESOURCE_PACK("/resourcepacks"),
        DATA_PACK("/datapacks");

        private final String directory;
    }
}
