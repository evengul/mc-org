package app.mcorg.project.presentation.rest.project;

import app.mcorg.project.domain.usecase.UseCaseExecutor;
import app.mcorg.project.domain.usecase.project.*;
import app.mcorg.project.domain.usecase.schematic.CreateProjectFromMaterialListUseCase;
import app.mcorg.project.presentation.rest.common.aspect.CheckArchived;
import app.mcorg.project.presentation.rest.entities.GenericResponse;
import app.mcorg.project.presentation.rest.entities.project.CreateProjectRequest;
import app.mcorg.project.presentation.rest.entities.project.ProjectListResponse;
import app.mcorg.project.presentation.rest.entities.project.ProjectResponse;
import app.mcorg.project.presentation.rest.mappers.project.CreateProjectInputMapper;
import app.mcorg.project.presentation.rest.mappers.project.GetAllProjectsOutputMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class ProjectController implements ProjectResource {
    private final UseCaseExecutor executor;
    private final GetAllProjectsUseCase getAllProjectsUseCase;
    private final GetProjectUseCase getProjectUseCase;
    private final CreateProjectUseCase createProjectUseCase;
    private final CreateProjectFromMaterialListUseCase createProjectFromMaterialListUseCase;
    private final DeleteAllProjectsUseCase deleteAllProjectsUseCase;
    private final DeleteProjectUseCase deleteProjectUseCase;
    private final ArchiveProjectUseCase archiveProjectUseCase;
    private final UnarchiveProjectUseCase unarchiveProjectUseCase;
    private final IsProjectArchivedUseCase isProjectArchivedUseCase;

    @Override
    public CompletableFuture<ResponseEntity<ProjectListResponse>> get() {
        return executor.execute(
                getAllProjectsUseCase,
                new GetAllProjectsUseCase.InputValues(),
                GetAllProjectsOutputMapper::map
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<GenericResponse>> delete(String id) {
        return executor.execute(
                deleteProjectUseCase,
                new DeleteProjectUseCase.InputValues(id),
                outputValues -> GenericResponse.ok("Project deleted")
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<GenericResponse>> deleteAll(Boolean confirm) {
        return executor.execute(
                deleteAllProjectsUseCase,
                new DeleteAllProjectsUseCase.InputValues(confirm),
                outputValues -> GenericResponse.ok("All projects deleted")
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<ProjectResponse>> getProject(String id) {
        return executor.execute(
                getProjectUseCase,
                new GetProjectUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(ProjectResponse.from(outputValues.project()))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<GenericResponse>> createProject(CreateProjectRequest request) {
        return executor.execute(
                createProjectUseCase,
                CreateProjectInputMapper.map(request),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project created successfully"))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<GenericResponse>> createFromMaterialList(CreateProjectRequest request, MultipartFile file) throws IOException {
        return executor.execute(
                createProjectFromMaterialListUseCase,
                new CreateProjectFromMaterialListUseCase.InputValues(request.teamId(), request.worldId(), request.name(), file.getInputStream()),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project created successfully"))
        );
    }

    @Override
    @CheckArchived("id")
    public CompletableFuture<ResponseEntity<GenericResponse>> archiveProject(String id) {
        return executor.execute(
                archiveProjectUseCase,
                new ArchiveProjectUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project archived successfully"))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<GenericResponse>> unarchiveProject(String id) {
        return executor.execute(
                unarchiveProjectUseCase,
                new UnarchiveProjectUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project unarchived successfully"))
        );
    }

    @Override
    public CompletableFuture<ResponseEntity<Boolean>> isArchived(String id) {
        return executor.execute(
                isProjectArchivedUseCase,
                new IsProjectArchivedUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(outputValues.isArchived())
        );
    }
}
