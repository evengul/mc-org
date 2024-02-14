package app.mcorg.project.presentation.rest.project;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.project.domain.usecase.UseCaseExecutor;
import app.mcorg.project.domain.usecase.project.*;
import app.mcorg.project.domain.usecase.schematic.CreateProjectFromMaterialListUseCase;
import app.mcorg.project.presentation.rest.common.aspect.CheckAccess;
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
    private final OpenProjectUseCase openProjectUseCase;
    private final IsProjectArchivedUseCase isProjectArchivedUseCase;

    @Override
    public CompletableFuture<ResponseEntity<ProjectListResponse>> get() {
        return executor.execute(
                getAllProjectsUseCase,
                new GetAllProjectsUseCase.InputValues(),
                GetAllProjectsOutputMapper::map);
    }

    @Override
    @CheckAccess(authority = Authority.ADMIN)
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
    @CheckAccess(authority = Authority.PARTICIPANT)
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
    @CheckAccess(authority = Authority.ADMIN)
    public CompletableFuture<ResponseEntity<GenericResponse>> archiveProject(String id) {
        return executor.execute(
                archiveProjectUseCase,
                new ArchiveProjectUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project archived successfully"))
        );
    }

    @Override
    @CheckAccess(authority = Authority.ADMIN)
    public CompletableFuture<ResponseEntity<GenericResponse>> openProject(String id) {
        return executor.execute(
                openProjectUseCase,
                new OpenProjectUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(new GenericResponse(true, "Project unarchived successfully"))
        );
    }

    @Override
    @CheckAccess(authority = Authority.PARTICIPANT)
    public CompletableFuture<ResponseEntity<Boolean>> isArchived(String id) {
        return executor.execute(
                isProjectArchivedUseCase,
                new IsProjectArchivedUseCase.InputValues(id),
                outputValues -> ResponseEntity.ok(outputValues.isArchived())
        );
    }
}
