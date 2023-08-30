package app.mcorg.common.event.project;

public record ProjectNameChanged(String id,
                                 String name) implements ProjectEvent {
}
