package app.mcorg.resources.domain.model.resource;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Resource {

    private final String name;
    private final Type type;
    private final String url;

    public static Resource create(String name, Type type) {
        return new Resource(name, type, null);
    }

    public static Resource create(String name, Type type, String url) {
        return new Resource(name, type, url);
    }

    @SuppressWarnings("unused")
    public Resource withUrl(String url) {
        return new Resource(name, type, url);
    }

    @RequiredArgsConstructor
    public enum Type {
        MOD,
        RESOURCE_PACK,
        DATA_PACK
    }
}
