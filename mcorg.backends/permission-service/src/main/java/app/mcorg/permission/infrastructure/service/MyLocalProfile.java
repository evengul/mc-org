package app.mcorg.permission.infrastructure.service;

import app.mcorg.permission.domain.api.MyProfile;
import app.mcorg.permission.domain.model.permission.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("todo")
@Primary
public class MyLocalProfile implements MyProfile {
    @Override
    public Profile get() {
        return new Profile("d5ec6cd1-8e8a-4835-b544-809ee6dbcd2d", "lilpebblez");
    }

    @Override
    public Profile get(String microsoftAccessToken) {
        return get();
    }

    @Override
    public String getMinecraftJwt(String microsoftAccessToken) {
        return null;
    }
}
