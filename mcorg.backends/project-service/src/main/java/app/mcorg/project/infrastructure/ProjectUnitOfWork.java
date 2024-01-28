package app.mcorg.project.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.project.ProjectEvent;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.infrastructure.entities.mappers.ProjectMapper;
import app.mcorg.project.infrastructure.repository.MongoProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectUnitOfWork implements UnitOfWork<Project> {

    private final MongoProjectRepository repository;
    private final EventDispatcher<ProjectEvent> dispatch;

    @Override
    public Project add(Project aggregateRoot) {
        Project stored = ProjectMapper.mapOut(
                repository.save(ProjectMapper.mapIn(aggregateRoot))
        );

        dispatch.dispatch(aggregateRoot.getDomainEvents());

        return stored;
    }

    @Override
    public void remove(String id) {
        repository.deleteById(id);
    }
}
