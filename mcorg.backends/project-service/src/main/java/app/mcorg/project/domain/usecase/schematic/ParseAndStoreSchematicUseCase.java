package app.mcorg.project.domain.usecase.schematic;

import app.mcorg.project.domain.model.schematic.Schematic;
import app.mcorg.project.domain.usecase.UseCase;
import lombok.RequiredArgsConstructor;

@SuppressWarnings("unused")
@RequiredArgsConstructor
public class ParseAndStoreSchematicUseCase extends UseCase<ParseAndStoreSchematicUseCase.InputValues, ParseAndStoreSchematicUseCase.OutputValues> {

    public OutputValues execute(InputValues input) {
        return new OutputValues(null);
    }

    public record InputValues(String name) implements UseCase.InputValues {
    }

    public record OutputValues(Schematic schematic) implements UseCase.OutputValues {
    }
}