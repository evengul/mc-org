package app.minecraftorganizer.minecraftserver.infrastructure.server;

import app.minecraftorganizer.minecraftserver.core.usecase.server.DockerService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.ExposedPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerServiceImpl implements DockerService {

    private final DockerClient client;

    @Override
    public String startServer(String version, List<String> modUrls) {
        log.info(String.format("Starting server with version %s", version));
        String id = client.createContainerCmd("itzg/minecraft-server")
                .withName(String.format("MC-%s", version))
                .withEnv("EULA=\"TRUE`", String.format("VERSION=%s", version), "TYPE=FABRIC")
                .withExposedPorts(ExposedPort.tcp(25565), ExposedPort.tcp(25575))
                .withTty(true)
                .withStdinOpen(true)
                .exec()
                .getId();
        log.info(String.format("Container with %s started", id));

        addMods(modUrls);

        return id;
    }

    private void addMods(List<String> modUrls) {

    }
}
