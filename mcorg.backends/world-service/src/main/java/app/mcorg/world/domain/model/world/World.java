package app.mcorg.world.domain.model.world;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.event.world.WorldCreated;
import app.mcorg.common.event.world.WorldEvent;
import app.mcorg.common.event.world.WorldNameChanged;
import app.mcorg.world.domain.model.team.SlimTeam;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import org.bson.types.ObjectId;

import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class World extends AggregateRoot<WorldEvent> {
    private final String id;
    private String name;
    private final List<String> users;
    private final List<SlimTeam> teams;

    public static World create(@NonNull String name, @NonNull String creator) {
        World world = new World(ObjectId.get().toHexString(), name, List.of(creator), Collections.emptyList());
        world.raiseEvent(new WorldCreated(world.id, world.name, world.users.get(0)));
        return world;
    }

    public void setName(@NonNull String name) {
        this.name = name;
        this.raiseEvent(new WorldNameChanged(this.id, name));
    }

    public void addUser(@NonNull String user) {
        if (this.users.stream().noneMatch(existing -> existing.equalsIgnoreCase(user))) {
            this.users.add(user);
        }
    }

    public void removeUser(@NonNull String username) {
        this.users.removeIf(user -> user.equalsIgnoreCase(username));
    }

    public void addTeam(SlimTeam team) {
        if (this.teams.stream().noneMatch(existing -> existing.id().equals(team.id()))) {
            this.teams.add(team);
        }
    }

    public void removeTeam(String id) {
        this.teams.removeIf(team -> team.id().equals(id));
    }

    public void changeTeamName(String id, String name) {
        if (this.teams.stream().anyMatch(team -> team.id().equals(id))) {
            removeTeam(id);
            addTeam(new SlimTeam(id, name));
        }
    }
}
