package app.mcorg.project.infrastructure.dispatcher;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.team.TeamEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamEventDispatcher implements EventDispatcher<TeamEvent> {
    private final ApplicationEventPublisher publisher;

    @Override
    public void dispatch(TeamEvent event) {
        publisher.publishEvent(event);
    }

}
