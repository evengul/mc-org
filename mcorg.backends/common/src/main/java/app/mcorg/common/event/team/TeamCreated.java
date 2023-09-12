package app.mcorg.common.event.team;

import app.mcorg.common.domain.model.SlimUser;

public record TeamCreated(String id,
                          String worldId,
                          String name,
                          SlimUser creator) implements TeamEvent {
}
