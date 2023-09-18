package app.mcorg.common.event.team;

public record TeamNameChanged(String id,
                              String worldId,
                              String name) implements TeamEvent {
}
