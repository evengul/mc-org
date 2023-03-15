package app.mcorg.organizer.presentation.rest.project.task;

import app.mcorg.organizer.domain.model.project.Priority;
import app.mcorg.organizer.domain.usecase.UseCaseExecutor;
import app.mcorg.organizer.domain.usecase.project.task.*;
import app.mcorg.organizer.presentation.rest.common.aspect.CheckArchived;
import app.mcorg.organizer.presentation.rest.entities.GenericResponse;
import app.mcorg.organizer.presentation.rest.entities.project.AddTaskRequest;
import app.mcorg.organizer.presentation.rest.entities.project.SimpleProjectResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class TaskController implements TaskResource {
    private final UseCaseExecutor executor;
    private final ConvertTaskToProjectUseCase convertTaskToProjectUseCase;
    private final AddTaskUseCase addTaskUseCase;
    private final RemoveTaskUseCase removeTaskUseCase;
    private final RenameTaskUseCase renameTaskUseCase;
    private final ReprioritizeTaskUseCase reprioritizeTaskUseCase;
    private final DoTaskUseCase doTaskUseCase;
    private final UndoTaskUseCase undoTaskUseCase;

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(String projectId, String taskId) {
        return executor.execute(
                convertTaskToProjectUseCase,
                new ConvertTaskToProjectUseCase.InputValues(projectId, taskId),
                outputValues -> ResponseEntity.ok(SimpleProjectResponse.from(outputValues.created()))
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> add(String projectId, AddTaskRequest request) {
        return executor.execute(
                addTaskUseCase,
                new AddTaskUseCase.InputValues(projectId, request.name(), request.priority()),
                outputValues -> GenericResponse.ok("Task was added successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, String taskId) {
        return executor.execute(
                removeTaskUseCase,
                new RemoveTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task was removed successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, String taskId, String name) {
        return executor.execute(
                renameTaskUseCase,
                new RenameTaskUseCase.InputValues(projectId, taskId, name),
                outputValues -> GenericResponse.ok("Task was renamed successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, String taskId, Priority priority) {
        return executor.execute(
                reprioritizeTaskUseCase,
                new ReprioritizeTaskUseCase.InputValues(projectId, taskId, priority),
                outputValues -> GenericResponse.ok("Task was reprioritized successfully")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> complete(String projectId, String taskId) {
        return executor.execute(
                doTaskUseCase,
                new DoTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task is now done")
        );
    }

    @Override
    @CheckArchived
    public CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(String projectId, String taskId) {
        return executor.execute(
                undoTaskUseCase,
                new UndoTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task had been undone")
        );
    }
}
