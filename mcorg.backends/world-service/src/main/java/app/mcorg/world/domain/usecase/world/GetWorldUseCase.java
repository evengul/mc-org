package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.usecase.UseCase;
import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.exceptions.NotFoundException;
import app.mcorg.world.domain.model.world.World;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetWorldUseCase extends UseCase<GetWorldUseCase.InputValues, GetWorldUseCase.OutputValues> {

    private final Worlds worlds;

    public OutputValues execute(InputValues input) {
        final String id = input.id();
        return new OutputValues(worlds.get(id).orElseThrow(() -> NotFoundException.world(id)));
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}