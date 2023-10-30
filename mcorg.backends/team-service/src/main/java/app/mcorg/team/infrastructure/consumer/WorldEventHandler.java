package app.mcorg.team.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class WorldEventHandler {
    private final Teams teams;
    private final UnitOfWork<Team> unitOfWork;

    @Bean
    public Consumer<WorldDeleted> worldDeletedConsumer() {
        return event -> teams.getTeamsInWorld(event.id())
                .forEach(team -> unitOfWork.remove(team.getId()));
    }
}
