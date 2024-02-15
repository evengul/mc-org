package app.mcorg.project.domain.exceptions;

public class NotFoundException extends DomainException {
    private NotFoundException(String resource, String notFound) {
        super(resource, notFound);
    }

    public static NotFoundException project(String projectId) {
        return new NotFoundException("resource.project", projectId);
    }

    public static NotFoundException task(String taskId) {
        return new NotFoundException("resource.project.task", taskId);
    }

    public static NotFoundException user(String username) {
        return new NotFoundException("resource.user", username);
    }
}
