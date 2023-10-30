package app.mcorg.team.infrastructure.dispatcher;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.common.event.team.TeamEvent;
import app.mcorg.common.event.team.TeamNameChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamEventDispatcher implements EventDispatcher<TeamEvent> {

    private final StreamBridge streamBridge;

    @Override
    public void dispatch(TeamEvent event) {
        switch (event) {
            case TeamCreated created -> streamBridge.send("team-created-events", created);
            case TeamNameChanged nameChanged -> streamBridge.send("team-name-changed-events", nameChanged);
            case TeamDeleted deleted -> streamBridge.send("team-deleted-events", deleted);
        }
    }
}
