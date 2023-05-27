package app.mcorg.server.core.usecase.server;

import java.util.List;

public interface DockerService {
    String startServer(String version, List<String> modUrls);

    @SuppressWarnings("unused")
    void stopServer(String version);
}
