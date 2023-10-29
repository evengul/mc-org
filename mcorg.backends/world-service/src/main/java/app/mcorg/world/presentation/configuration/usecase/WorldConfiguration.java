package app.mcorg.world.presentation.configuration.usecase;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.domain.usecase.world.ChangeWorldNameUseCase;
import app.mcorg.world.domain.usecase.world.CreateWorldUseCase;
import app.mcorg.world.domain.usecase.world.DeleteWorldUseCase;
import app.mcorg.world.domain.usecase.world.GetWorldUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorldConfiguration {
    @Bean
    public GetWorldUseCase getWorldUseCase(Worlds worlds) {
        return new GetWorldUseCase(worlds);
    }

    @Bean
    public ChangeWorldNameUseCase changeWorldNameUseCase(GetWorldUseCase getWorldUseCase, UnitOfWork<World> unitOfWork) {
        return new ChangeWorldNameUseCase(getWorldUseCase, unitOfWork);
    }

    @Bean
    public DeleteWorldUseCase deleteWorldUseCase(UnitOfWork<World> unitOfWork) {
        return new DeleteWorldUseCase(unitOfWork);
    }

    @Bean
    public CreateWorldUseCase createWorldUseCase(UsernameProvider usernameProvider, UnitOfWork<World> unitOfWork) {
        return new CreateWorldUseCase(usernameProvider, unitOfWork);
    }
}
