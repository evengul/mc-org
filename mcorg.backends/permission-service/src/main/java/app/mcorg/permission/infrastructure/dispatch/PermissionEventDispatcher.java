package app.mcorg.permission.infrastructure.dispatch;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.permission.PermissionEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionEventDispatcher implements EventDispatcher<PermissionEvent> {
    private final ApplicationEventPublisher publisher;

    @Override
    public void dispatch(PermissionEvent event) {
        publisher.publishEvent(event);
    }
}
