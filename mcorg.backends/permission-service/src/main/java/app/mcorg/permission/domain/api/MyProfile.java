package app.mcorg.permission.domain.api;

import app.mcorg.permission.domain.model.permission.Profile;

public interface MyProfile {

    Profile get();

    Profile get(String microsoftAccessToken);

    String getMinecraftJwt(String microsoftAccessToken);
}
