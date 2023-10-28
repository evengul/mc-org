package app.mcorg.common.event.project;

public record ProjectCreated(String id,
                             String teamId,
                             String worldId,
                             String name,
                             String creator) implements ProjectEvent {
}
