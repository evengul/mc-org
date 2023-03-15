package app.minecraftorganizer.minecraftserver.presentation.configuration.usecase;

import app.minecraftorganizer.minecraftserver.core.usecase.mods.GetModsUseCase;
import app.minecraftorganizer.minecraftserver.core.usecase.server.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

@Configuration
public class ServerConfiguration {
    @Bean
    public GetServerStatusUseCase getServerStatusUseCase(ServerRepository repository) {
        return new GetServerStatusUseCase(repository);
    }
    @Bean
    public UpdateServerStatusUseCase updateServerStatusUseCase(ServerRepository repository) {
        return new UpdateServerStatusUseCase(repository);
    }
    @Bean
    public StartServerUseCase startServerUseCase(DockerService dockerService, GetModsUseCase getModsUseCase, UpdateServerStatusUseCase updateServerStatusUseCase, Executor executor) {
        return new StartServerUseCase(dockerService, getModsUseCase, updateServerStatusUseCase, executor);
    }
}
