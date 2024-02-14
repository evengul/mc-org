package app.mcorg.project.domain.model.team;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamEvent;
import app.mcorg.common.event.team.TeamNameChanged;
import app.mcorg.project.domain.model.project.SlimProject;
import app.mcorg.project.domain.model.world.SlimWorld;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Team extends AggregateRoot<TeamEvent> {
    private final String id;
    private String name;
    private final SlimWorld world;
    private final List<SlimProject> projects;
    private final List<SlimUser> users;

    public static Team create(String name, SlimWorld world, SlimUser creator) {
        Team team = new Team(ObjectId.get().toHexString(), name, world, new ArrayList<>(), new ArrayList<>());
        team.addCreator(world.id(), creator);
        return team;
    }

    public void addCreator(String worldId, SlimUser creator) {
        this.raiseEvent(new TeamCreated(id, name, worldId, creator));
    }

    public void addProject(String projectId, String projectName) {
        this.projects.add(new SlimProject(projectId, projectName));
    }

    public void removeProject(String projectId) {
        this.projects.removeIf(project -> project.id().equals(projectId));
    }

    public void setName(String name) {
        this.name = name;
        this.raiseEvent(new TeamNameChanged(id, name));
    }

    public SlimTeam toSlim() {
        return new SlimTeam(id, name);
    }
}
