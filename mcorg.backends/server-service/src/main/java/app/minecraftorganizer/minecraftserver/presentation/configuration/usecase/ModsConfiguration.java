package app.minecraftorganizer.minecraftserver.presentation.configuration.usecase;

import app.minecraftorganizer.minecraftserver.core.usecase.mods.GetModsUseCase;
import app.minecraftorganizer.minecraftserver.core.usecase.mods.ModRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModsConfiguration {
    @Bean
    public GetModsUseCase getModsUseCase(ModRepository repository) {
        return new GetModsUseCase(repository);
    }
}
