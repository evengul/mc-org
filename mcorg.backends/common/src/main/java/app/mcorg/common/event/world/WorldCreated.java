package app.mcorg.common.event.world;

import app.mcorg.common.domain.model.SlimUser;

public record WorldCreated(String id,
                           String name,
                           SlimUser creator) implements WorldEvent {
}
