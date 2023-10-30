package app.mcorg.team.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.project.ProjectCreated;
import app.mcorg.common.event.project.ProjectDeleted;
import app.mcorg.common.event.project.ProjectNameChanged;
import app.mcorg.team.domain.model.project.SlimProject;
import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.domain.usecase.team.GetTeamUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class ProjectEventHandler {
    private final GetTeamUseCase getTeamUseCase;
    private final UnitOfWork<Team> unitOfWork;

    @Bean
    public Consumer<ProjectCreated> projectCreatedConsumer() {
        return event -> editTeam(event.teamId(), team -> team.addProject(new SlimProject(event.id(), event.name())));
    }

    @Bean
    public Consumer<ProjectNameChanged> projectNameChangedConsumer() {
        return event -> editTeam(event.teamId(), team -> team.changeProjectName(event.id(), event.name()));
    }

    @Bean
    public Consumer<ProjectDeleted> projectDeletedConsumer() {
        return event -> editTeam(event.teamId(), team -> team.removeProject(event.id()));
    }

    private void editTeam(String teamId, Consumer<Team> edit) {
        Team team = getTeamUseCase.execute(new GetTeamUseCase.InputValues(teamId)).team();
        edit.accept(team);
        unitOfWork.add(team);
    }

}
