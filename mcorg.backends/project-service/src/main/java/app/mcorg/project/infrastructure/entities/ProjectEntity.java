package app.mcorg.project.infrastructure.entities;

import app.mcorg.project.domain.model.project.ProjectDependency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.stream.Stream;

@Document("project")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProjectEntity {
    @Id
    private String id;
    private String teamId;
    private String worldId;
    private String name;
    private Boolean isArchived;
    private List<DoableTaskEntity> tasks;
    private List<CountedTaskEntity> countedTasks;
    private List<ProjectDependency> dependencies;
    private List<String> users;

    public Stream<DoableTaskEntity> tasks() {
        return this.tasks.stream();
    }

    public Stream<CountedTaskEntity> countedTasks() {
        return this.countedTasks.stream();
    }
}
