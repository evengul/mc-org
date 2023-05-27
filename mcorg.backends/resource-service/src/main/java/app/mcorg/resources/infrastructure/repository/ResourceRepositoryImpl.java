package app.mcorg.resources.infrastructure.repository;

import app.mcorg.resources.domain.api.Resources;
import app.mcorg.resources.domain.model.resource.ResourcePack;
import app.mcorg.resources.infrastructure.repository.entities.ResourcePackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ResourceRepositoryImpl implements Resources {

    private final MongoResourceRepository repository;

    @Override
    public Optional<ResourcePack> getResourcePack(String id) {
        return repository.findById(id)
                .map(ResourcePackMapper::mapOut);
    }

    @Override
    public ResourcePack persist(ResourcePack resourcePack) {
        return ResourcePackMapper.mapOut(
                repository.save(ResourcePackMapper.mapIn(resourcePack))
        );
    }

    @Override
    public List<ResourcePack> getResourcePacks() {
        return repository.findAll()
                .stream()
                .map(ResourcePackMapper::mapOut)
                .toList();
    }

    @Override
    public List<ResourcePack> getResourcePacks(String version) {
        return repository.findAllByVersionEqualsIgnoreCase(ResourcePackMapper.mapIn(version))
                .stream()
                .map(ResourcePackMapper::mapOut)
                .toList();
    }
}
