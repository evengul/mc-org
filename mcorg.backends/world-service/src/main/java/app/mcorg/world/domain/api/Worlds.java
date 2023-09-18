package app.mcorg.world.domain.api;

import app.mcorg.world.domain.model.world.World;

import java.util.List;
import java.util.Optional;

public interface Worlds {
    Optional<World> get(String id);

    List<World> getWorldsWithUser(String username);
}
