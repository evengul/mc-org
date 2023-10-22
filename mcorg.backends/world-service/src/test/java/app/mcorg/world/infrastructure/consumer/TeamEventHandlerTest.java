package app.mcorg.world.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.common.event.team.TeamNameChanged;
import app.mcorg.world.MongoContainerTest;
import app.mcorg.world.WorldService;
import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.model.team.SlimTeam;
import app.mcorg.world.domain.model.world.World;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RunWith(SpringRunner.class)
@SpringBootTest(classes = WorldService.class)
public class TeamEventHandlerTest extends MongoContainerTest {

    @Autowired
    Consumer<TeamCreated> handleTeamCreated;
    @Autowired
    Consumer<TeamNameChanged> handleTeamNameChanged;
    @Autowired
    Consumer<TeamDeleted> handleTeamDeleted;

    @Autowired
    Worlds worlds;
    @Autowired
    UnitOfWork<World> unitOfWork;

    @Test
    public void handleCreateTeam() {
        // Given
        String worldId = createWorld();
        TeamCreated event = new TeamCreated(ObjectId.get().toHexString(), worldId, "TeamName", new SlimUser("EVEGUL", "Even"));

        // When
        handleTeamCreated.accept(event);

        World world = worlds.get(worldId).orElseThrow();

        // Then
        assertThat(world.getTeams()).hasSize(1);
        assertThat(world.getTeams().get(0).id()).isEqualTo(event.id());
    }

    @Test
    public void handleTeamNameChanged() {
        // Given
        SlimTeam team = new SlimTeam(ObjectId.get().toHexString(), "TeamName");
        String worldId = createWorld(List.of(team));
        String newName = "New Name";
        TeamNameChanged event = new TeamNameChanged(team.id(), worldId, newName);

        // When
        handleTeamNameChanged.accept(event);

        World world = worlds.get(worldId).orElseThrow();

        // Then
        assertThat(world.getTeams()).hasSize(1);
        assertThat(world.getTeams().get(0).id()).isEqualTo(team.id());
        assertThat(world.getTeams().get(0).name()).isEqualTo(newName);
    }

    @Test
    public void handleTeamDeleted() {
        // Given
        SlimTeam team = new SlimTeam(ObjectId.get().toHexString(), "TeamName");
        String worldId = createWorld(List.of(team));
        TeamDeleted event = new TeamDeleted(team.id(), worldId);

        // When
        handleTeamDeleted.accept(event);

        World world = worlds.get(worldId).orElseThrow();

        // Then
        assertThat(world.getTeams()).isEmpty();
    }

    private String createWorld() {
        return createWorld(Collections.emptyList());
    }

    private String createWorld(List<SlimTeam> teams) {
        return unitOfWork.add(new World(
                ObjectId.get().toHexString(),
                "WorldName",
                List.of(new SlimUser("EVEGUL", "Even")),
                teams
        )).getId();
    }
}
