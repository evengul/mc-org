package app.mcorg.common.event.team;

import app.mcorg.common.domain.model.SlimUser;

public record TeamCreated(String id,
                          String name,
                          String worldId,
                          SlimUser creator) implements TeamEvent {
}
