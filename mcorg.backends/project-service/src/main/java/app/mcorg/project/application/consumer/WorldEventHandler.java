package app.mcorg.project.application.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class WorldEventHandler {
    private final UnitOfWork<Project> unitOfWork;
    private final Projects projects;

    @Bean
    public Consumer<WorldDeleted> teamDeletedProject() {
        return event -> projects.getProjectsInWorld(event.id())
                .forEach(project -> unitOfWork.remove(project.getId()));
    }
}
