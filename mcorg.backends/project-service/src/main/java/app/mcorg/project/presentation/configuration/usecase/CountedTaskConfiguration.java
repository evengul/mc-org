package app.mcorg.project.presentation.configuration.usecase;

import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.AddCountedTaskUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.CountedTaskDoneMoreUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.CountedTaskNeedsMoreUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.RemoveTaskUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CountedTaskConfiguration {
    @Bean
    public AddCountedTaskUseCase addCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new AddCountedTaskUseCase(getProjectUseCase, storeProjectUseCase);
    }

    @Bean
    public RemoveTaskUseCase removeCountedTaskUseCase(GetProjectUseCase getProjectUseCase, StoreProjectUseCase storeProjectUseCase) {
        return new RemoveTaskUseCase(getProjectUseCase, storeProjectUseCase);
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
