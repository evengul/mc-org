package app.mcorg.project.domain.model.project.task;

import app.mcorg.project.domain.model.project.Priority;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.project.ProjectDependency;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DoableTask extends Task {
    private boolean isDone;

    public DoableTask(UUID id, String name, Priority priority, boolean isDone, List<ProjectDependency> projectDependencies) {
        super(id, name, priority, projectDependencies);
        this.isDone = isDone;
    }

    @Override
    public boolean isDone() {
        return this.isDone;
    }

    public static DoableTask newInstance(String name, Priority priority) {
        return new DoableTask(null, name, priority, false, Collections.emptyList());
    }

    public Project convertToProject() {
        return Project.newInstance(this.getName());
    }

    public void undone() {
        this.isDone = false;
    }

    public void done() {
        this.isDone = true;
    }

}
