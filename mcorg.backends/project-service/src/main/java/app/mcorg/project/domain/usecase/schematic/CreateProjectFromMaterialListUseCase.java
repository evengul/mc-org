package app.mcorg.project.domain.usecase.schematic;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.project.domain.api.SchematicParser;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.schematic.Schematic;
import app.mcorg.project.domain.usecase.UseCase;
import app.mcorg.project.domain.usecase.project.StoreProjectUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class CreateProjectFromMaterialListUseCase extends
                                                  UseCase<CreateProjectFromMaterialListUseCase.InputValues, CreateProjectFromMaterialListUseCase.OutputValues> {

    private final UserProvider userProvider;
    private final GetTeamUseCase getTeamUseCase;
    private final StoreProjectUseCase storeProjectUseCase;
    private final UsernameProvider usernameProvider;

    public OutputValues execute(InputValues input) {
        final String teamId = input.teamId();
        final String worldId = input.worldId();
        final String username = usernameProvider.get();
        final String name = input.name();
        final InputStream file = input.file();
        Schematic schematic = getSchematic(name, file);

        // TODO: Add all project members of task to this new project
        Project project = Project.from(List.of(username), teamId, worldId, schematic);

        return store(project);
    }

    private Schematic getSchematic(String name, InputStream file) {
        return SchematicParser.parseMaterialList(name, file);
    }

    private OutputValues store(Project project) {
        return new OutputValues(storeProjectUseCase.execute(new StoreProjectUseCase.InputValues(project)).project());
    }

    public record InputValues(String teamId, String worldId, String name,
                              InputStream file) implements UseCase.InputValues {
    }

    public record OutputValues(Project project) implements UseCase.OutputValues {
    }
}
