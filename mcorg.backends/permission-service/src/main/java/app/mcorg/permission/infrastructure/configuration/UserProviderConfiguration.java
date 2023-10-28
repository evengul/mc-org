package app.mcorg.permission.infrastructure.configuration;

import app.mcorg.common.domain.api.UsernameProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
public class UserProviderConfiguration {
    @Bean
    public UsernameProvider username() {
        return () -> SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
    }
}
