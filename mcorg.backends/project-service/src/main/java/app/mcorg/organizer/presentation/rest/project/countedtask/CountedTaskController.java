package app.mcorg.organizer.presentation.rest.project.countedtask;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.domain.usecase.UseCaseExecutor;
import app.mcorg.organizer.domain.usecase.project.countedtask.*;
import app.mcorg.organizer.presentation.rest.common.aspect.CheckArchived;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddCountedTaskRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class CountedTaskController implements CountedTaskResource {
    private final UseCaseExecutor executor;
    private final AddCountedTaskUseCase addCountedTaskUseCase;
    private final RemoveCountedTaskUseCase removeCountedTaskUseCase;
    private final RenameCountedTaskUseCase renameCountedTaskUseCase;
    private final ReprioritizeCountedTaskUseCase reprioritizeCountedTaskUseCase;
    private final CountedTaskNeedsMoreUseCase needsMoreUseCase;
    private final CountedTaskDoneMoreUseCase doneMoreUseCase;

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> add(String projectId, AddCountedTaskRequest request) {
        return executor.execute(
                addCountedTaskUseCase,
                new AddCountedTaskUseCase.InputValues(projectId, request.name(), request.priority(), request.needed()),
                outputValues -> GenericResponse.ok("Counted task successfully added to project")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, String taskId) {
        return executor.execute(
                removeCountedTaskUseCase,
                new RemoveCountedTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Counted task successfully removed from project")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, String taskId, String name) {
        return executor.execute(
                renameCountedTaskUseCase,
                new RenameCountedTaskUseCase.InputValues(projectId, taskId, name),
                outputValues -> GenericResponse.ok("Task renamed successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, String taskId, Priority priority) {
        return executor.execute(
                reprioritizeCountedTaskUseCase,
                new ReprioritizeCountedTaskUseCase.InputValues(projectId, taskId, priority),
                outputValues -> GenericResponse.ok("Task reprioritized successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> needsMore(String projectId, String taskId, int needed) {
        return executor.execute(
                needsMoreUseCase,
                new CountedTaskNeedsMoreUseCase.InputValues(projectId, taskId, needed),
                outputValues -> GenericResponse.ok("Requirement was changed sucessfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> doneMore(String projectId, String taskId, int done) {
        return executor.execute(
                doneMoreUseCase,
                new CountedTaskDoneMoreUseCase.InputValues(projectId, taskId, done),
                outputValues -> GenericResponse.ok("Work registered successfully")
        );
    }
}
