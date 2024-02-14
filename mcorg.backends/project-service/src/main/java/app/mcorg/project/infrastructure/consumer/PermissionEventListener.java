package app.mcorg.project.infrastructure.consumer;

import app.mcorg.common.event.permission.PermissionEvent;
import app.mcorg.project.domain.exceptions.NotFoundException;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.infrastructure.entities.mappers.ProjectMapper;
import app.mcorg.project.infrastructure.repository.MongoProjectRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
public class PermissionEventListener {
    private final MongoProjectRepository projects;

    @EventListener
    public void onApplicationEvent(@NotNull PermissionEvent event) {
        switch (event) {

        }
    }

    private void editProject(String id, Consumer<Project> consumer) {
        Project project = ProjectMapper.toDomain(projects.findById(id).orElseThrow(() -> new NotFoundException(id)));
        consumer.accept(project);
        projects.save(ProjectMapper.toEntity(project));
    }
}
