package app.mcorg.project.application.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class TeamEventHandler {
    private final UnitOfWork<Project> unitOfWork;
    private final Projects projects;

    @Bean
    public Consumer<TeamDeleted> teamDeletedProject() {
        return event -> projects.getProjectsInTeam(event.id())
                .forEach(project -> unitOfWork.remove(project.getId()));
    }

}
