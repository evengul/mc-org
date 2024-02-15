package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.project.Project;

import java.util.List;
import java.util.Optional;

public interface Projects {
    List<Project> get();
    
    Optional<Project> get(String id);

    List<Project> getProjectsInTeam(String teamId);

    List<Project> getProjectsInWorld(String worldId);

    List<Project> getProjectsWithUser(String username);

    void deleteAll();

    void delete(String id);

    boolean isArchived(String id);
}
