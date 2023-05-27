package app.mcorg.project.domain.usecase.schematic;

import app.mcorg.project.domain.api.SchematicParser;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.schematic.Schematic;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;

@Slf4j
@RequiredArgsConstructor
public class CreateProjectFromMaterialListUseCase extends UseCase<CreateProjectFromMaterialListUseCase.InputValues, CreateProjectFromMaterialListUseCase.OutputValues> {

    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String name = input.name();
        final InputStream file = input.file();
        Schematic schematic = getSchematic(name, file);

        Project project = Project.from(schematic);

        return store(project);
    }

    private Schematic getSchematic(String name, InputStream file) {
        return SchematicParser.parseMaterialList(name, file);
    }

    private OutputValues store(Project project) {
        return new OutputValues(storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project());
    }

    public record InputValues(String name, InputStream file) implements UseCase.InputValues { }

    public record OutputValues(Project project) implements UseCase.OutputValues { }
}