package app.mcorg.organizer.presentation.configuration.usecase;

import app.mcorg.organizer.domain.api.ProjectRepository;
import app.mcorg.organizer.domain.usecase.project.*;
import app.mcorg.organizer.domain.usecase.schematic.CreateProjectFromMaterialListUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProjectConfiguration {
    @Bean
    public GetAllProjectsUseCase getAllProjectsUseCase(ProjectRepository repository) {
        return new GetAllProjectsUseCase(repository);
    }
    @Bean
    public GetProjectUseCase getProjectUseCase(ProjectRepository repository) {
        return new GetProjectUseCase(repository);
    }
    @Bean
    public IsProjectArchivedUseCase isProjectArchivedUseCase(ProjectRepository repository) {
        return new IsProjectArchivedUseCase(repository);
    }
    @Bean
    public StoreProjectUseCase storeProjectUseCase(ProjectRepository repository) {
        return new StoreProjectUseCase(repository);
    }
    @Bean
    public DeleteAllProjectsUseCase deleteAllProjectsUseCase(ProjectRepository repository) {
        return new DeleteAllProjectsUseCase(repository);
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
