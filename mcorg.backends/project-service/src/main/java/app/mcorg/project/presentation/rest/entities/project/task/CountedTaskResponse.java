package app.mcorg.project.presentation.rest.entities.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.minecraft.Item;
import app.mcorg.project.domain.model.minecraft.ItemCategory;
import app.mcorg.project.domain.model.project.task.CountedTask;
import app.mcorg.project.presentation.rest.entities.project.ProjectDependencyResponse;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record CountedTaskResponse(@NonNull UUID id,
                                  @NonNull String name,
                                  @NonNull Priority priority,
                                  @NonNull int needed,
                                  @NonNull int done,
                                  ItemCategory category,
                                  @NonNull List<ProjectDependencyResponse> projectDependencies) {
    public static CountedTaskResponse from(CountedTask countedTask) {
        return new CountedTaskResponse(
                countedTask.getId(),
                countedTask.getName(),
                countedTask.getPriority(),
                countedTask.getNeeded(),
                countedTask.getDone(),
                Optional.ofNullable(countedTask.getItem()).map(Item::getCategory).orElse(null),
                countedTask.getProjectDependencies()
                           .stream()
                           .map(ProjectDependencyResponse::from)
                           .toList()
        );
    }
}
