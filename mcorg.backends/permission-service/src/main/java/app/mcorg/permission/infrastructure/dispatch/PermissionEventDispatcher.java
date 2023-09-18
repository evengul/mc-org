package app.mcorg.permission.infrastructure.dispatch;

import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.permission.*;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PermissionEventDispatcher implements EventDispatcher<PermissionEvent> {
    private final StreamBridge streamBridge;

    @Override
    public void dispatch(PermissionEvent event) {
        switch (event) {
            case UserDeleted deleted -> streamBridge.send("user-deleted-events", deleted);
            case AuthorityAdded added -> streamBridge.send("user-authority-added-events", added);
            case AuthorityChanged changed -> streamBridge.send("user-authority-changed-events", changed);
            case AuthorityRemoved removed -> streamBridge.send("user-authority-removed", removed);
        }
    }
}
