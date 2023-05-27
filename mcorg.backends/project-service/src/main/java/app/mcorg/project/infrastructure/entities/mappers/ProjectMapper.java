package app.mcorg.project.infrastructure.entities.mappers;

import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.project.task.Task;
import app.mcorg.project.domain.model.project.task.Tasks;
import app.mcorg.project.infrastructure.entities.ProjectEntity;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UtilityClass
public class ProjectMapper {
    public static ProjectEntity mapIn(Project project) {
        return new ProjectEntity(
                project.getId(),
                project.getName(),
                project.isArchived(),
                project.doableTasks().map(TaskMapper::mapIn).toList(),
                project.countedTasks().map(CountedTaskMapper::mapIn).toList(),
                project.getProjectDependencies()
        );
    }

    public static Project mapOut(ProjectEntity entity) {
        List<Task> tasks = new ArrayList<>();
        tasks.addAll(entity.tasks().map(TaskMapper::mapOut).toList());
        tasks.addAll(entity.countedTasks().map(CountedTaskMapper::mapOut).toList());
        return new Project(
                entity.getId(),
                entity.getName(),
                entity.getIsArchived(),
                Optional.ofNullable(entity.getDependencies()).orElse(new ArrayList<>()),
                Tasks.create(tasks)
        );
    }
}
