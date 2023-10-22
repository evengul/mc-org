package app.mcorg.world.domain.model.world;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.domain.model.SlimUser;
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
    private final List<SlimUser> users;
    private final List<SlimTeam> teams;

    public static World create(@NonNull String name, @NonNull SlimUser creator) {
        return new World(ObjectId.get().toHexString(), name, List.of(creator), Collections.emptyList())
                .markCreated();
    }

    private World markCreated() {
        this.raiseEvent(new WorldCreated(this.id, this.name, this.users.get(0)));
        return this;
    }

    public void setName(@NonNull String name) {
        this.name = name;
        this.raiseEvent(new WorldNameChanged(this.id, name));
    }

    public void addUser(@NonNull SlimUser user) {
        if (this.users.stream().noneMatch(existing -> existing.username().equalsIgnoreCase(user.username()))) {
            this.users.add(user);
        }
    }

    public void removeUser(@NonNull String username) {
        this.users.removeIf(user -> user.username().equalsIgnoreCase(username));
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
