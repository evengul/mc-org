package app.minecraftorganizer.minecraftserver.core.usecase.server;

import app.minecraftorganizer.minecraftserver.core.model.Server;
import app.minecraftorganizer.minecraftserver.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static java.util.Objects.nonNull;

@Slf4j
@RequiredArgsConstructor
public class UpdateServerStatusUseCase extends UseCase<UpdateServerStatusUseCase.InputValues, UpdateServerStatusUseCase.OutputValues> {

    private final ServerRepository repository;

    public OutputValues execute(InputValues input) {
        final String containerId = input.containerId();
        final String version = input.version();
        final Server.Status status = input.status();
        final String message = input.message();
        Server server = Server.newInstance(version, status, message);
        if(nonNull(containerId)) {
            server = server.withId(containerId);
        }
        repository.persist(server);
        log.info(String.format("Server with id=%s, version %s and message %s updated to status %s", containerId, version, message, status));
        return new OutputValues();
    }

    public record InputValues(String containerId, String version, Server.Status status, String message) implements UseCase.InputValues {
    }

    public record OutputValues() implements UseCase.OutputValues {
    }
}