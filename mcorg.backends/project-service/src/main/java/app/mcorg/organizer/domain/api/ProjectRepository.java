package app.mcorg.organizer.domain.api;

import app.mcorg.organizer.domain.model.project.Project;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    List<Project> get();
    Project persist(Project project);
    Optional<Project> get(String id);
    void deleteAll();
    boolean isArchived(String id);
}
