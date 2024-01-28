package app.mcorg.project.infrastructure.dispatcher;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.project.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectEventDispatcher implements EventDispatcher<ProjectEvent> {

    private final StreamBridge streamBridge;

    @Override
    public void dispatch(ProjectEvent event) {
        switch (event) {
            case ProjectCreated created -> streamBridge.send("project-created-events", created);
            case ProjectNameChanged nameChanged -> streamBridge.send("project-name-changed-events", nameChanged);
            case ProjectDeleted deleted -> streamBridge.send("project-deleted-events", deleted);
            case ProjectDependencyAddedToTask addedToTask ->
                    streamBridge.send("project-dependency-added-to-task-events", addedToTask);
        }
    }
}
