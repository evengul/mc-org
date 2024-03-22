package app.mcorg.permission.infrastructure.configuration;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.permission.domain.api.MyProfile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserProviderConfiguration {
    @Bean
    public UsernameProvider username(MyProfile myProfile) {
        return () -> myProfile.get().minecraftId();
    }
}
