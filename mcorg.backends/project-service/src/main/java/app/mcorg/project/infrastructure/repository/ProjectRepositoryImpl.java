package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.infrastructure.entities.mappers.ProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements Projects {

    private final MongoProjectRepository repository;

    @Override
    public List<Project> get() {
        return repository.findAll()
                .stream()
                .map(ProjectMapper::mapOut)
                .toList();
    }

    @Override
    public Project persist(Project project) {
        return ProjectMapper.mapOut(repository.save(ProjectMapper.mapIn(project)));
    }

    @Override
    public Optional<Project> get(String id) {
        return repository.findById(id)
                .map(ProjectMapper::mapOut);
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }

    @Override
    public void delete(String id) {
        repository.deleteById(id);
    }

    @Override
    public boolean isArchived(String id) {
        return get(id).map(Project::isArchived).orElse(false);
    }
}
