package app.mcorg.team.domain.model.team;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamEvent;
import app.mcorg.common.event.team.TeamNameChanged;
import app.mcorg.team.domain.model.project.SlimProject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class Team extends AggregateRoot<TeamEvent> {
    private final String id;
    private final String worldId;
    private String name;
    private final List<String> users;
    private final List<SlimProject> projects;

    public static Team create(@NonNull String name, @NonNull String creator, @NonNull String worldId) {
        Team team = new Team(ObjectId.get().toHexString(), worldId, name, List.of(creator), Collections.emptyList());
        team.raiseEvent(new TeamCreated(team.id, team.worldId, team.name, team.users.get(0)));
        return team;
    }

    public void setName(@NonNull String name) {
        this.name = name;
        this.raiseEvent(new TeamNameChanged(this.id, this.worldId, name));
    }

    public void addUser(@NonNull String user) {
        if (this.users.stream().noneMatch(existing -> existing.equalsIgnoreCase(user))) {
            this.users.add(user);
        }
    }

    public void removeUser(@NonNull String username) {
        this.users.removeIf(user -> user.equalsIgnoreCase(username));
    }

    public void addProject(SlimProject project) {
        if (this.projects.stream().noneMatch(existing -> existing.id().equalsIgnoreCase(project.id()))) {
            this.projects.add(project);
        }
    }

    public void removeProject(String id) {
        this.projects.removeIf(project -> project.id().equalsIgnoreCase(id));
    }

    public void changeProjectName(String id, String name) {
        if (this.projects.stream().anyMatch(project -> project.id().equalsIgnoreCase(id))) {
            removeProject(id);
            addProject(new SlimProject(id, name));
        }
    }
}
