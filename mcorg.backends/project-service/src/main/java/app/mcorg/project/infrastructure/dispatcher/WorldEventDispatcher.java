package app.mcorg.project.infrastructure.dispatcher;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.world.WorldEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorldEventDispatcher implements EventDispatcher<WorldEvent> {
    private final ApplicationEventPublisher publisher;

    @Override
    public void dispatch(WorldEvent event) {
        publisher.publishEvent(event);
    }
}
