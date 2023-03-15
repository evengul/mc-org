package app.minecraftorganizer.minecraftserver.core.usecase.server;

import app.minecraftorganizer.minecraftserver.core.model.Server;

import java.util.Optional;

public interface ServerRepository {
    Server persist(Server server);
    Optional<Server> get(String version);
    void delete(String version);
}
