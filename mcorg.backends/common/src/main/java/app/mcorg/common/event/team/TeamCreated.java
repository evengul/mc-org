package app.mcorg.common.event.team;

public record TeamCreated(String id,
                          String worldId,
                          String name,
                          String creator) implements TeamEvent {
}
