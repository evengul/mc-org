package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.project.domain.model.project.task.DoableTask;
import app.mcorg.project.infrastructure.entities.DoableTaskEntity;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class TaskMapper {
    public static DoableTaskEntity toEntity(DoableTask doableTask) {
        return new DoableTaskEntity(
                Optional.ofNullable(doableTask.getId()).orElse(UUID.randomUUID()),
                doableTask.getName(),
                doableTask.getPriority(),
                doableTask.isDone(),
                doableTask.getProjectDependencies()
        );
    }

    public static DoableTask toDomain(DoableTaskEntity entity) {
        return new DoableTask(
                entity.id(),
                entity.name(),
                entity.priority(),
                entity.isDone(),
                Optional.ofNullable(entity.projectDependencies()).orElse(new ArrayList<>())
        );
    }
}
