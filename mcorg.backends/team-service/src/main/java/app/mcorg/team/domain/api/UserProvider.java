package app.mcorg.team.domain.api;

import app.mcorg.team.domain.model.permission.SlimUser;

public interface UserProvider {
    SlimUser get();
}
