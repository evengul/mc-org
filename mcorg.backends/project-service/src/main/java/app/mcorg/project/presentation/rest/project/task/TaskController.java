package app.mcorg.project.presentation.rest.project.task;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.usecase.UseCaseExecutor;
import app.mcorg.project.domain.usecase.project.countedtask.AddCountedTaskUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.CountedTaskDoneMoreUseCase;
import app.mcorg.project.domain.usecase.project.countedtask.CountedTaskNeedsMoreUseCase;
import app.mcorg.project.domain.usecase.project.task.*;
import app.mcorg.project.presentation.rest.common.aspect.CheckAccess;
import app.mcorg.project.presentation.rest.common.aspect.CheckArchived;
import app.mcorg.project.presentation.rest.entities.GenericResponse;
import app.mcorg.project.presentation.rest.entities.project.SimpleProjectResponse;
import app.mcorg.project.presentation.rest.entities.project.task.AddCountedTaskRequest;
import app.mcorg.project.presentation.rest.entities.project.task.AddTaskRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.UUID;
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

    private final AddCountedTaskUseCase addCountedTaskUseCase;
    private final CountedTaskNeedsMoreUseCase needsMoreUseCase;
    private final CountedTaskDoneMoreUseCase doneMoreUseCase;

    private final AddProjectDependencyToTaskUseCase addProjectDependencyToTaskUseCase;

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<SimpleProjectResponse>> convert(String projectId, UUID taskId) {
        return executor.execute(
                convertTaskToProjectUseCase,
                new ConvertTaskToProjectUseCase.InputValues(projectId, taskId),
                outputValues -> ResponseEntity.ok(SimpleProjectResponse.from(outputValues.created()))
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> addDoable(String projectId, AddTaskRequest request) {
        return executor.execute(
                addTaskUseCase,
                new AddTaskUseCase.InputValues(projectId, request.name(), request.priority()),
                outputValues -> GenericResponse.ok("Task was added successfully")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> remove(String projectId, UUID taskId) {
        return executor.execute(
                removeTaskUseCase,
                new RemoveTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task was removed successfully")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> rename(String projectId, UUID taskId, String name) {
        return executor.execute(
                renameTaskUseCase,
                new RenameTaskUseCase.InputValues(projectId, taskId, name),
                outputValues -> GenericResponse.ok("Task was renamed successfully")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> reprioritize(String projectId, UUID taskId,
                                                                           Priority priority) {
        return executor.execute(
                reprioritizeTaskUseCase,
                new ReprioritizeTaskUseCase.InputValues(projectId, taskId, priority),
                outputValues -> GenericResponse.ok("Task was reprioritized successfully")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> complete(String projectId, UUID taskId) {
        return executor.execute(
                doTaskUseCase,
                new DoTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task is now done")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> uncomplete(String projectId, UUID taskId) {
        return executor.execute(
                undoTaskUseCase,
                new UndoTaskUseCase.InputValues(projectId, taskId),
                outputValues -> GenericResponse.ok("Task had been undone")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> addCountable(String projectId,
                                                                           AddCountedTaskRequest request) {
        return executor.execute(
                addCountedTaskUseCase,
                new AddCountedTaskUseCase.InputValues(projectId, request.name(), request.priority(), request.needed()),
                outputValues -> GenericResponse.ok("Counted task successfully added to project")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> needsMore(String projectId, UUID taskId, int needed) {
        return executor.execute(
                needsMoreUseCase,
                new CountedTaskNeedsMoreUseCase.InputValues(projectId, taskId, needed),
                outputValues -> GenericResponse.ok("Requirement was changed sucessfully")
                               );
    }

    @Override
    @CheckArchived
    @CheckAccess(authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> doneMore(String projectId, UUID taskId, int done) {
        return executor.execute(
                doneMoreUseCase,
                new CountedTaskDoneMoreUseCase.InputValues(projectId, taskId, done),
                outputValues -> GenericResponse.ok("Work registered successfully")
                               );
    }

    @Override
    @CheckArchived("projectIdOfTask")
    @CheckAccess(value = "projectIdOfTask", authority = Authority.PARTICIPANT, level = AuthorityLevel.TASK)
    public CompletableFuture<ResponseEntity<GenericResponse>> dependsOn(String projectIdOfTask,
                                                                        UUID taskId,
                                                                        String dependencyProjectId,
                                                                        Priority priority) {
        return executor.execute(
                addProjectDependencyToTaskUseCase,
                new AddProjectDependencyToTaskUseCase.InputValues(projectIdOfTask, taskId, dependencyProjectId,
                                                                  priority),
                outputValues -> GenericResponse.ok("Dependency added")
                               );
    }
}
