package app.mcorg.world.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.common.event.team.TeamNameChanged;
import app.mcorg.world.domain.model.team.SlimTeam;
import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.domain.usecase.world.GetWorldUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class TeamEventHandler {

    private final GetWorldUseCase getWorldUseCase;
    private final UnitOfWork<World> unitOfWork;

    @Bean
    public Consumer<TeamCreated> teamCreatedConsumer() {
        return event -> editWorld(event.worldId(), world -> world.addTeam(new SlimTeam(event.id(), event.name())));
    }

    @Bean
    public Consumer<TeamNameChanged> teamNameChangedConsumer() {
        return event -> editWorld(event.worldId(), world -> world.changeTeamName(event.id(), event.name()));
    }

    @Bean
    public Consumer<TeamDeleted> teamDeletedConsumer() {
        return event -> editWorld(event.worldId(), world -> world.removeTeam(event.id()));
    }

    private void editWorld(String worldId, Consumer<World> edit) {
        World world = getWorldUseCase.execute(new GetWorldUseCase.InputValues(worldId)).world();
        edit.accept(world);
        unitOfWork.add(world);
    }
}
