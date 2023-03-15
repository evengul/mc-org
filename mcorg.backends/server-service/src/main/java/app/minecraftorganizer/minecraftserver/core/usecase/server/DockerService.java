package app.minecraftorganizer.minecraftserver.core.usecase.server;

import java.util.List;

public interface DockerService {
    String startServer(String version, List<String> modUrls);
}
