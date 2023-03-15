package app.minecraftorganizer.minecraftserver.infrastructure.server;

import app.minecraftorganizer.minecraftserver.core.model.Server;
import app.minecraftorganizer.minecraftserver.core.usecase.server.ServerRepository;
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
