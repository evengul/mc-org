package app.minecraftorganizer.minecraftserver.core.usecase.server;

import app.minecraftorganizer.minecraftserver.core.model.Mod;
import app.minecraftorganizer.minecraftserver.core.model.Server;
import app.minecraftorganizer.minecraftserver.core.model.exceptions.DomainException;
import app.minecraftorganizer.minecraftserver.core.usecase.UseCase;
import app.minecraftorganizer.minecraftserver.core.usecase.mods.GetModsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@RequiredArgsConstructor
public class StartServerUseCase extends UseCase<StartServerUseCase.InputValues, StartServerUseCase.OutputValues> {

    private final DockerService dockerService;
    private final GetModsUseCase getModsUseCase;
    private final UpdateServerStatusUseCase updateServerStatusUseCase;

    private final Executor executor;

    @Override
    public OutputValues execute(InputValues inputValues) {
        final String version = inputValues.version();

        updateStatus(null, version, Server.Status.BOOTING);

        executor.execute(() -> {
            try {
                List<String> mods = getModUrls(version);

                String containerId = dockerService.startServer(version, mods);

                updateStatus(containerId, version, Server.Status.RUNNING);
            } catch (DomainException e) {
                log.error("Domain exception occurred while starting server", e);
                updateStatus(null, version, Server.Status.ERROR, e.getLocalizedMessage());
            } catch (Exception e) {
                log.error("Non-domain exception occurred while starting server", e);
                updateStatus(null, version, Server.Status.ERROR, "UNKNOWN ERROR, check logs for details");
            }
        });

        return new OutputValues();
    }

    private void updateStatus(String containerId, String version, Server.Status status, String message) {
        updateServerStatusUseCase.execute(new UpdateServerStatusUseCase.InputValues(containerId, version, status, message));
    }

    private void updateStatus(String containerId, String version, Server.Status status) {
        updateServerStatusUseCase.execute(new UpdateServerStatusUseCase.InputValues(containerId, version, status, null));
    }

    private List<String> getModUrls(String version) {
        return getModsUseCase.execute(new GetModsUseCase.InputValues(version))
                .mods().stream()
                .map(Mod::url)
                .toList();
    }

    public record InputValues(String version) implements UseCase.InputValues {}
    public record OutputValues() implements UseCase.OutputValues {}
}
