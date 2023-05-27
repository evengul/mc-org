package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.project.Project;

import java.util.List;
import java.util.Optional;

public interface Projects {
    List<Project> get();

    Project persist(Project project);

    Optional<Project> get(String id);

    void deleteAll();

    void delete(String id);

    boolean isArchived(String id);
}
