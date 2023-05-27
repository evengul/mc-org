package app.mcorg.server.presentation.configuration.usecase;

import app.mcorg.server.core.usecase.mods.GetModsUseCase;
import app.mcorg.server.core.usecase.mods.ModRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModsConfiguration {
    @Bean
    public GetModsUseCase getModsUseCase(ModRepository repository) {
        return new GetModsUseCase(repository);
    }
}
