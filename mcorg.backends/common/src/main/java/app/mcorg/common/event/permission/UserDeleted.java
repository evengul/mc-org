package app.mcorg.common.event.permission;

public record UserDeleted(String username) implements PermissionEvent {
}
