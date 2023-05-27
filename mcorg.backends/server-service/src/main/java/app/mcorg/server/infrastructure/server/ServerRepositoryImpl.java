package app.mcorg.server.infrastructure.server;

import app.mcorg.server.core.model.Server;
import app.mcorg.server.core.usecase.server.ServerRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ServerRepositoryImpl implements ServerRepository {
    @Override
    public Server persist(Server server) {
        return server;
    }

    @Override
    public Optional<Server> get(String version) {
        return Optional.empty();
    }

    @Override
    public void delete(String version) {

    }
}
