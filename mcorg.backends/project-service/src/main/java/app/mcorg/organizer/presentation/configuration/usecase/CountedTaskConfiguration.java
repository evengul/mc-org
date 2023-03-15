package app.mcorg.organizer.presentation.configuration.usecase;

import app.mcorg.organizer.domain.usecase.project.GetProjectUseCase;
import app.mcorg.organizer.domain.usecase.project.StoreProjectUseCase;
import app.mcorg.organizer.domain.usecase.project.countedtask.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CountedTaskConfiguration {
    @Bean
    public AddCountedTaskUseCase addCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new AddCountedTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }
    @Bean
    public RemoveCountedTaskUseCase removeCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new RemoveCountedTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public RenameCountedTaskUseCase renameCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new RenameCountedTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }
    @Bean
    public ReprioritizeCountedTaskUseCase reprioritizeCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new ReprioritizeCountedTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }
    @Bean
    public CountedTaskNeedsMoreUseCase countedTaskNeedsMoreUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new CountedTaskNeedsMoreUseCase(getProjectUseCase, storeProjectUseCase);
    }
    @Bean
    public CountedTaskDoneMoreUseCase countedTaskDoneMoreUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new CountedTaskDoneMoreUseCase(getProjectUseCase, storeProjectUseCase);
    }
}
