package app.mcorg.permission.infrastructure.dispatch;

import app.mcorg.common.event.DomainEvent;
import app.mcorg.common.event.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionEventDispatcher implements EventDispatcher {
    private final ApplicationEventPublisher publisher;

    @Override
    public void dispatch(DomainEvent event) {
        publisher.publishEvent(event);
    }
}
