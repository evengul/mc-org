package app.mcorg.server.infrastructure.server;

import app.mcorg.server.core.usecase.server.DockerService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerServiceImpl implements DockerService {

    private final DockerClient client;

    private final static ExposedPort MAIN_PORT = ExposedPort.tcp(25565);
    private final static ExposedPort RCON_PORT = ExposedPort.tcp(25575);

    @Override
    public String startServer(String version, List<String> modUrls) {
        log.info(String.format("Starting server with version %s", version));

        String containerName = getContainerName(version);

        createVolumeIfNotExists(containerName);

        String id = containerFromName(containerName)
                .map(Container::getId)
                .or(() -> Optional.of(client.createContainerCmd("itzg/minecraft-server")
                        .withName(getContainerName(version))
                        .withEnv("EULA=TRUE", String.format("VERSION=%s", version), "TYPE=FABRIC")
                        .withExposedPorts(MAIN_PORT, RCON_PORT)
                        .withHostConfig(hostConfig(containerName))
                        .withTty(true)
                        .withStdinOpen(true)
                        .exec()
                        .getId()))
                .get();

        if (!containerRunning(containerName)) {
            client.startContainerCmd(id)
                    .exec();
        }

        log.info(String.format("Container with %s started", id));

        addMods(modUrls);

        return id;
    }

    @Override
    public void stopServer(String version) {
        String containerName = getContainerName(version);
        if (containerRunning(containerName)) {
            client.stopContainerCmd(containerName)
                    .exec();
        }
    }

    private void createVolumeIfNotExists(String containerName) {
        client.listVolumesCmd()
                .exec()
                .getVolumes()
                .stream()
                .filter(volume -> volume.getName().equals(containerName))
                .findFirst()
                .ifPresentOrElse(volume -> log.debug("Volume {} already created, skipping.", containerName),
                        () -> client.createVolumeCmd()
                                .withName(containerName)
                                .exec());
    }

    private boolean containerRunning(String containerName) {
        return containerFromName(containerName)
                .filter(container -> container.getState().equalsIgnoreCase("RUNNING"))
                .isPresent();
    }

    private Optional<Container> containerFromName(String containerName) {
        return client.listContainersCmd()
                .withShowAll(true)
                .withNameFilter(List.of(containerName))
                .exec()
                .stream()
                .findFirst();
    }

    private HostConfig hostConfig(String volumeName) {
        Ports ports = new Ports();
        ports.bind(MAIN_PORT, Ports.Binding.bindPort(MAIN_PORT.getPort()));
        ports.bind(RCON_PORT, Ports.Binding.bindPort(RCON_PORT.getPort()));

        Volume volume = new Volume("/data");

        return new HostConfig()
                .withBinds(new Bind(volumeName, volume))
                .withPortBindings(ports);
    }

    private String getContainerName(String version) {
        return String.format("MC-%s", version);
    }

    @SuppressWarnings("unused")
    private void addMods(List<String> modUrls) {

    }
}
