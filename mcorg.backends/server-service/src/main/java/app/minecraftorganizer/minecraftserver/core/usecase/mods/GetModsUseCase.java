package app.minecraftorganizer.minecraftserver.core.usecase.mods;

import app.minecraftorganizer.minecraftserver.core.model.Mod;
import app.minecraftorganizer.minecraftserver.core.usecase.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetModsUseCase extends UseCase<GetModsUseCase.InputValues, GetModsUseCase.OutputValues> {

    private final ModRepository repository;

    public OutputValues execute(InputValues input) {
        final String minecraftVersion = input.minecraftVersion();

        List<Mod> mods = getMods(minecraftVersion);

        return new OutputValues(mods);
    }

    List<Mod> getMods(String minecraftVersion) {
        return repository.getForVersion(minecraftVersion);
    }

    public record InputValues(String minecraftVersion) implements UseCase.InputValues {
    }

    public record OutputValues(List<Mod> mods) implements UseCase.OutputValues {
    }
}