package app.mcorg.resources.presentation.configuration;

import app.mcorg.resources.domain.api.Resources;
import app.mcorg.resources.domain.usecases.resource.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UsecaseConfiguration {
    @Bean
    public GetResourceUseCase getResourceUseCase(Resources resources) {
        return new GetResourceUseCase(resources);
    }

    @Bean
    public GetAllResourcesUseCase getAllResourcesUseCase(Resources resources) {
        return new GetAllResourcesUseCase(resources);
    }

    @Bean
    public GetResourcePacksInVersion getResourcesInVersionUseCase(Resources resources) {
        return new GetResourcePacksInVersion(resources);
    }

    @Bean
    public StoreResourceUseCase storeResourceUseCase(Resources resources) {
        return new StoreResourceUseCase(resources);
    }

    @Bean
    public AddResourceUseCase addResourceUseCase(GetResourceUseCase getResourceUseCase, StoreResourceUseCase storeResourceUseCase) {
        return new AddResourceUseCase(getResourceUseCase, storeResourceUseCase);
    }
}
