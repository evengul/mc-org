package app.mcorg.permission.presentation.rest.entities;

import app.mcorg.common.domain.model.AuthorityLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum AuthorityLevelEntity {
    WORLD(AuthorityLevel.WORLD),
    TEAM(AuthorityLevel.TEAM),
    PROJECT(AuthorityLevel.PROJECT),
    TASK(AuthorityLevel.TASK);

    private final AuthorityLevel domainValue;

    public static AuthorityLevelEntity from(AuthorityLevel authorityLevel) {
        return switch (authorityLevel) {
            case null -> null;
            case WORLD -> WORLD;
            case TEAM -> TEAM;
            case PROJECT -> PROJECT;
            case TASK -> TASK;
        };
    }
}
