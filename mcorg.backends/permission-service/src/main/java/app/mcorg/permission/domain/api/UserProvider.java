package app.mcorg.permission.domain.api;

import app.mcorg.common.domain.model.SlimUser;

public interface UserProvider {
    SlimUser get();
}
