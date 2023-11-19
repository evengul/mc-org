package app.mcorg.common.event.project;

public record ProjectDeleted(String id, String teamId) implements ProjectEvent {
}
