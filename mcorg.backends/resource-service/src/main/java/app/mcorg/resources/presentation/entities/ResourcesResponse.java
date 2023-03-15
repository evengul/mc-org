package app.mcorg.resources.presentation.entities;

import app.mcorg.resources.domain.model.resource.Resource;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;

public record ResourcesResponse(@NonNull String version, @NonNull Map<Resource.Type, List<NameAndUrl>> resourceTypes) {
    public static ResourcesResponse from(String version, List<Resource> resources) {
        if(resources.isEmpty()) {
            return new ResourcesResponse(version, emptyMap());
        }
        Map<Resource.Type, List<NameAndUrl>> typeMap = resources.stream()
                .collect(Collectors.groupingBy(Resource::getType,
                        HashMap::new,
                        Collectors.mapping(resource -> resource.getUrls()
                                        .entrySet()
                                        .stream()
                                        .filter(entry -> entry.getKey().equals(version))
                                        .findFirst()
                                        .map(entry -> new NameAndUrl(resource.getName(), entry.getValue()))
                                        .orElse(null),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        elements -> elements.stream().filter(Objects::nonNull).toList()))));
        return new ResourcesResponse(version, typeMap);
    }

    public record NameAndUrl(@NonNull String name, @NonNull String url) {}
}
