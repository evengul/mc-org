package app.mcorg.project.presentation.configuration.usecase;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.project.*;
import app.mcorg.project.domain.usecase.schematic.CreateProjectFromMaterialListUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfiguration {
    @Bean
    public GetAllProjectsUseCase getAllProjectsUseCase(Projects repository) {
        return new GetAllProjectsUseCase(repository);
    }

    @Bean
    public GetProjectUseCase getProjectUseCase(Projects repository) {
        return new GetProjectUseCase(repository);
    }

    @Bean
    public IsProjectArchivedUseCase isProjectArchivedUseCase(Projects repository) {
        return new IsProjectArchivedUseCase(repository);
    }

    @Bean
    public StoreProjectUseCase storeProjectUseCase(UnitOfWork<Project> unit) {
        return new StoreProjectUseCase(unit);
    }

    @Bean
    public DeleteAllProjectsUseCase deleteAllProjectsUseCase(Projects repository) {
        return new DeleteAllProjectsUseCase(repository);
    }

    @Bean
    public DeleteProjectUseCase deleteProjectUseCase(Projects projects) {
        return new DeleteProjectUseCase(projects);
    }

    @Bean
    public CreateProjectUseCase createProjectUseCase(UsernameProvider usernameProvider,
                                                     StoreProjectUseCase storeProjectUseCase) {
        return new CreateProjectUseCase(storeProjectUseCase, usernameProvider);
    }

    @Bean
    public CreateProjectFromMaterialListUseCase createProjectFromMaterialListUseCase(UsernameProvider usernameProvider,
                                                                                     StoreProjectUseCase storeProjectUseCase) {
        return new CreateProjectFromMaterialListUseCase(storeProjectUseCase, usernameProvider);
    }

    @Bean
    public ArchiveProjectUseCase archiveProjectUseCase(GetProjectUseCase getProjectUseCase,
                                                       StoreProjectUseCase storeProjectUseCase) {
        return new ArchiveProjectUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public OpenProjectUseCase unarchiveProjectUseCase(GetProjectUseCase getProjectUseCase,
                                                      StoreProjectUseCase storeProjectUseCase) {
        return new OpenProjectUseCase(getProjectUseCase, storeProjectUseCase);
    }
}
