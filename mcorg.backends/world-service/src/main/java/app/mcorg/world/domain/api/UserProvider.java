package app.mcorg.world.domain.api;

import app.mcorg.world.domain.model.permission.SlimUser;

public interface UserProvider {
    SlimUser get();
}
