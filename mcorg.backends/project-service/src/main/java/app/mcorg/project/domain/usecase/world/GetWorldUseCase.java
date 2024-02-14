package app.mcorg.project.domain.usecase.world;

import app.mcorg.project.domain.api.Worlds;
import app.mcorg.project.domain.model.world.World;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetWorldUseCase extends UseCase<GetWorldUseCase.InputValues, GetWorldUseCase.OutputValues> {

    private final Worlds worlds;

    @Override
    public OutputValues execute(InputValues input) {
        return new OutputValues(worlds.get(input.id));
    }

    public record InputValues(String id) implements UseCase.InputValues {
    }

    public record OutputValues(World world) implements UseCase.OutputValues {
    }
}
