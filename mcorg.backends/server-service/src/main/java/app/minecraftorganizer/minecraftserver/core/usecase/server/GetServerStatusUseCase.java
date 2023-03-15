package app.minecraftorganizer.minecraftserver.core.usecase.server;

import app.minecraftorganizer.minecraftserver.core.model.Server;
import app.minecraftorganizer.minecraftserver.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetServerStatusUseCase extends UseCase<GetServerStatusUseCase.InputValues, GetServerStatusUseCase.OutputValues> {

    private final ServerRepository repository;

    public OutputValues execute(InputValues input) {
        final String version = input.version();
        Server server = repository.get(version)
                .orElse(Server.neverStarted(version));
        return new OutputValues(server);
    }

    public record InputValues(String version) implements UseCase.InputValues {
    }

    public record OutputValues(Server server) implements UseCase.OutputValues {
    }
}