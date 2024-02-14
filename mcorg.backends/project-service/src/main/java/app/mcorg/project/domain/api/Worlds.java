package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.world.World;

import java.util.List;

public interface Worlds {
    List<World> getAll(String username);

    World get(String id);
}
