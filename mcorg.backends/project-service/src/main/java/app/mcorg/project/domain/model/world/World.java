package app.mcorg.project.domain.model.world;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.common.event.world.WorldCreated;
import app.mcorg.common.event.world.WorldEvent;
import app.mcorg.common.event.world.WorldNameChanged;
import app.mcorg.project.domain.model.team.SlimTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class World extends AggregateRoot<WorldEvent> {
    private final String id;
    private String name;
    private final List<SlimTeam> teams;
    private final List<SlimUser> users;

    public static World create(String name, SlimUser creator) {
        World world = new World(ObjectId.get().toHexString(), name, new ArrayList<>(), new ArrayList<>());
        world.addCreator(creator);
        return world;
    }

    private void addCreator(SlimUser creator) {
        this.raiseEvent(new WorldCreated(id, name, creator));
    }

    public void addTeam(String teamId, String teamName) {
        this.teams.add(new SlimTeam(teamId, teamName));
    }

    public void removeTeam(String teamId) {
        this.teams.removeIf(team -> team.id().equals(teamId));
    }

    public void setName(String name) {
        this.name = name;
        this.raiseEvent(new WorldNameChanged(id, name));
    }

    public void addUser(SlimUser user) {
        this.users.add(user);
    }

    public void removeUser(String username) {
        this.users.removeIf(user -> user.username().equals(username));
    }

    public SlimWorld toSlim() {
        return new SlimWorld(id, name);
    }
}
