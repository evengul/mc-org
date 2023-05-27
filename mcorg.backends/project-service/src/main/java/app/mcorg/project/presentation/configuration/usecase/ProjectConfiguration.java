package app.mcorg.project.presentation.configuration.usecase;

import app.mcorg.project.domain.api.Projects;
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
    public StoreProjectUseCase storeProjectUseCase(Projects repository) {
        return new StoreProjectUseCase(repository);
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
    public CreateProjectUseCase createProjectUseCase(StoreProjectUseCase storeProjectUseCase) {
        return new CreateProjectUseCase(storeProjectUseCase);
    }

    @Bean
    public CreateProjectFromMaterialListUseCase createProjectFromMaterialListUseCase(StoreProjectUseCase storeProjectUseCase) {
        return new CreateProjectFromMaterialListUseCase(storeProjectUseCase);
    }

    @Bean
    public ArchiveProjectUseCase archiveProjectUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new ArchiveProjectUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public UnarchiveProjectUseCase unarchiveProjectUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new UnarchiveProjectUseCase(getProjectUseCase, storeProjectUseCase);
    }
}
