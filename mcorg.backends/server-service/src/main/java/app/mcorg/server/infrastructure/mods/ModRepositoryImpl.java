package app.mcorg.server.infrastructure.mods;

import app.mcorg.server.core.model.Mod;
import app.mcorg.server.core.usecase.mods.ModRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

import static java.util.Collections.emptyList;

@Repository
public class ModRepositoryImpl implements ModRepository {
    @Override
    public List<Mod> getForVersion(String minecraftVersion) {
        return emptyList();
    }
}
