package app.mcorg.permission.domain.model.permission;

import app.mcorg.common.domain.model.AuthorityLevel;

public record PermissionLevel(String id,
                              AuthorityLevel authorityLevel,
                              PermissionLevel parent) {
    public static PermissionLevel project(String projectId, String teamId, String worldId) {
        return new PermissionLevel(
                projectId,
                AuthorityLevel.PROJECT,
                new PermissionLevel(
                        teamId,
                        AuthorityLevel.TEAM,
                        new PermissionLevel(
                                worldId,
                                AuthorityLevel.WORLD,
                                null
                        )
                )
        );
    }

    public static PermissionLevel team(String teamId, String worldId) {
        return new PermissionLevel(
                teamId,
                AuthorityLevel.TEAM,
                new PermissionLevel(
                        worldId,
                        AuthorityLevel.WORLD,
                        null
                )
        );
    }

    public static PermissionLevel world(String worldId) {
        return new PermissionLevel(worldId, AuthorityLevel.WORLD, null);
    }
}
