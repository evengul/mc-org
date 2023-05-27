package app.mcorg.server.core.model;

public record Server(String containerId, String version, Status status, String message) {

    public static Server newInstance(String version, Status status, String message) {
        return new Server(null, version, status, message);
    }

    public Server withId(String id) {
        return new Server(id, this.version, this.status, this.message);
    }

    public static Server neverStarted(String version) {
        return Server.newInstance(version, Status.NEVER_STARTED, null);
    }

    public enum Status {
        NEVER_STARTED,
        NOT_RUNNING,
        BOOTING,
        RUNNING,
        STOPPING,
        ERROR
    }
}
