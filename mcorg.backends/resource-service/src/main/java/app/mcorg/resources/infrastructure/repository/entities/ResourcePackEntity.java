package app.mcorg.resources.infrastructure.repository.entities;

import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.model.resource.ServerType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@Document("ResourcePack")
@AllArgsConstructor
@RequiredArgsConstructor
public class ResourcePackEntity {
    @Id
    private String id;
    private String name;
    private String version;
    private ServerType serverType;
    private List<Resource> resources;
}
