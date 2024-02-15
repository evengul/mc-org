package app.mcorg.project.infrastructure.repository;

import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.exceptions.NotFoundException;
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
                         .map(ProjectMapper::toDomain)
                         .toList();
    }

    @Override
    public Optional<Project> get(String id) {
        return repository.findById(id)
                         .map(ProjectMapper::toDomain);
    }

    @Override
    public List<Project> getProjectsInTeam(String teamId) {
        return repository.findAllByTeamId(teamId)
                         .stream()
                         .map(ProjectMapper::toDomain)
                         .toList();
    }

    @Override
    public List<Project> getProjectsInWorld(String worldId) {
        return repository.findAllByWorldId(worldId)
                         .stream()
                         .map(ProjectMapper::toDomain)
                         .toList();
    }

    @Override
    public List<Project> getProjectsWithUser(String username) {
        return repository.findAllByUsersContainingIgnoreCase(username)
                         .stream()
                         .map(ProjectMapper::toDomain)
                         .toList();
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
        return get(id).map(Project::isArchived)
                      .orElseThrow(() -> NotFoundException.project(id));
    }
}
