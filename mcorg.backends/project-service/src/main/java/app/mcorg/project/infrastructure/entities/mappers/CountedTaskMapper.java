package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.project.domain.model.project.task.CountedTask;
import app.mcorg.project.infrastructure.entities.CountedTaskEntity;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

@UtilityClass
public class CountedTaskMapper {
    public static CountedTaskEntity mapIn(CountedTask task) {
        return new CountedTaskEntity(
                Optional.ofNullable(task.getId()).orElse(UUID.randomUUID()),
                task.getName(),
                task.getPriority(),
                task.getNeeded(),
                task.getDone(),
                task.getItem(),
                task.getProjectDependencies()
        );
    }

    public static CountedTask mapOut(CountedTaskEntity entity) {
        return new CountedTask(
                entity.id(),
                entity.name(),
                entity.priority(),
                entity.needed(),
                entity.done(),
                entity.item(),
                Optional.ofNullable(entity.projectDependencies()).orElse(new ArrayList<>())
        );
    }
}
