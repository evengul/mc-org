package app.mcorg.organizer.infrastructure.repository;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.model.project.Project;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProjectRepositoryImpl implements ProjectRepository {
    @Override
    public List<Project> get() {
        return null;
    }

    @Override
    public Project persist(Project project) {
        return null;
    }

    @Override
    public Optional<Project> get(String id) {
        return Optional.empty();
    }

    @Override
    public void deleteAll() {

    }

    @Override
    public boolean isArchived(String id) {
        return false;
    }
}
