package app.mcorg.server.core.usecase.mods;

import app.mcorg.server.core.model.Mod;

import java.util.List;

public interface ModRepository {
    List<Mod> getForVersion(String minecraftVersion);
}
