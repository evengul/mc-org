package app.mcorg.common.event.project;

import app.mcorg.common.domain.model.SlimUser;

public record ProjectCreated(String id,
                             String teamId,
                             String worldId,
                             String name,
                             SlimUser creator) implements ProjectEvent {
}
