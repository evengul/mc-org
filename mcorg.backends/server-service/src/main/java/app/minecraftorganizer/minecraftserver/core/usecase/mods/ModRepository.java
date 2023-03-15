package app.minecraftorganizer.minecraftserver.core.usecase.mods;

import app.minecraftorganizer.minecraftserver.core.model.Mod;

import java.util.List;

public interface ModRepository {
    List<Mod> getForVersion(String minecraftVersion);
}
