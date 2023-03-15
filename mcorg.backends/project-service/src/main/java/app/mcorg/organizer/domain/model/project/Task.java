package app.mcorg.organizer.domain.model.project;

public record Task(String id, String name, Priority priority, boolean isDone) {
    public static Task newInstance(String name, Priority priority) {
        return new Task(null, name, priority, false);
    }

    public Project convertToProject() {
        return Project.newInstance(this.name);
    }

    public Task rename(String name) {
        return new Task(
                this.id,
                name,
                this.priority,
                this.isDone
        );
    }

    public Task undone() {
        return new Task(
                this.id,
                this.name,
                this.priority,
                false
        );
    }

    public Task done() {
        return new Task(
                this.id,
                this.name,
                this.priority,
                true
        );
    }

    public Task prioritized(Priority priority) {
        return new Task(
                this.id,
                this.name,
                priority,
                this.isDone
        );
    }
}
