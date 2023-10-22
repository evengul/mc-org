package app.mcorg.common.event.team;

public record TeamDeleted(String id, String worldId) implements TeamEvent {
}
