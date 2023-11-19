package app.mcorg.common.event.project;

public record ProjectNameChanged(String id,
                                 String teamId,
                                 String name) implements ProjectEvent {
}
