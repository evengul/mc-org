package app.mcorg.common.event.team;

public record TeamNameChanged(String id,
                              String name) implements TeamEvent {
}
