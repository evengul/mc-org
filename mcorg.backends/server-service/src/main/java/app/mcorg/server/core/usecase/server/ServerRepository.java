package app.mcorg.server.core.usecase.server;

import app.mcorg.server.core.model.Server;

import java.util.Optional;

public interface ServerRepository {
    Server persist(Server server);

    Optional<Server> get(String version);

    @SuppressWarnings("unused")
    void delete(String version);
}
