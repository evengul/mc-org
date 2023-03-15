package app.mcorg.organizer.domain.usecase.schematic;

import app.mcorg.organizer.domain.api.SchematicParser;
import app.mcorg.organizer.domain.model.exceptions.SchematicParseException;
import app.mcorg.organizer.domain.model.project.Project;
import app.mcorg.organizer.domain.model.schematic.Schematic;
import app.mcorg.organizer.domain.usecase.UseCase;
import app.mcorg.organizer.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
public class CreateProjectFromMaterialListUseCase extends UseCase<CreateProjectFromMaterialListUseCase.InputValues, CreateProjectFromMaterialListUseCase.OutputValues> {

    private final StoreProjectUseCase storeProjectUseCase;

    public OutputValues execute(InputValues input) {
        final String name = input.name();
        final MultipartFile file = input.file();
        Schematic schematic = getSchematic(name, file);

        Project project = Project.from(schematic);

        return store(project);
    }

    private Schematic getSchematic(String name, MultipartFile file) {
        try {
            return SchematicParser.parseMaterialList(name, file.getInputStream());
        } catch (IOException e) {
            log.error("Error parsing file {}", name, e);
            throw new SchematicParseException(name);
        }
    }

    private OutputValues store(Project project) {
        return new OutputValues(storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project());
    }

    public record InputValues(String name, MultipartFile file) implements UseCase.InputValues { }

    public record OutputValues(Project project) implements UseCase.OutputValues { }
}