package app.mcorg.common.event.world;

public record WorldCreated(String id,
                           String name,
                           String creator) implements WorldEvent {
}
