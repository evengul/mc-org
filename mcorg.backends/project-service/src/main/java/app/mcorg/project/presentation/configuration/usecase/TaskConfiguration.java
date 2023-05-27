package app.mcorg.project.presentation.configuration.usecase;

import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import app.mcorg.project.domain.usecase.project.task.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskConfiguration {
    @Bean
    public ConvertTaskToProjectUseCase convertTaskToProjectUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new ConvertTaskToProjectUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public AddTaskUseCase addTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new AddTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public RemoveTaskUseCase removeTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new RemoveTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public RenameTaskUseCase renameTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new RenameTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public ReprioritizeTaskUseCase reprioritizeTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new ReprioritizeTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public DoTaskUseCase doTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new DoTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public UndoTaskUseCase undoTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new UndoTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public AddProjectDependencyToTaskUseCase addProjectDependencyToTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new AddProjectDependencyToTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }
}
