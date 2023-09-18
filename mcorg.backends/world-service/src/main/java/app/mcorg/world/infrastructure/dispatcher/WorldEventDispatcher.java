package app.mcorg.world.infrastructure.dispatcher;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.world.WorldCreated;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.common.event.world.WorldEvent;
import app.mcorg.common.event.world.WorldNameChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorldEventDispatcher implements EventDispatcher<WorldEvent> {

    private final StreamBridge streamBridge;

    @Override
    public void dispatch(WorldEvent event) {
        switch (event) {
            case WorldCreated created -> streamBridge.send("world-created-events", created);
            case WorldNameChanged changed -> streamBridge.send("world-name-changed-events", changed);
            case WorldDeleted deleted -> streamBridge.send("world-deleted-events", deleted);
        }
    }
}
