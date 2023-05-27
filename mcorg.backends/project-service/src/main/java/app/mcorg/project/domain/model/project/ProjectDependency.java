package app.mcorg.project.domain.model.project;

public record ProjectDependency(String projectId, Priority priority, Direction direction) {
    public enum Direction {
        DEPENDS_ON,
        IS_DEPENDED_ON_BY
    }
}
