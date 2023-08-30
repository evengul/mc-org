package app.mcorg.common.event.world;

public record WorldNameChanged(String id,
                               String name) implements WorldEvent {
}
