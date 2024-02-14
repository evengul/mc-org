package app.mcorg.project.infrastructure.configuration;

import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.project.domain.api.UserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

@Configuration
public class UsernameConfiguration {
    @Bean
    public UserProvider username() {
        return () -> {
            String username = SecurityContextHolder.getContext()
                                                   .getAuthentication()
                                                   .getName();
            String fullName = Optional.ofNullable(SecurityContextHolder.getContext()
                                                                       .getAuthentication()
                                                                       .getCredentials())
                                      .filter(Jwt.class::isInstance)
                                      .map(Jwt.class::cast)
                                      .map(jwt -> jwt.getClaim("name"))
                                      .filter(String.class::isInstance)
                                      .map(String.class::cast)
                                      .orElse(null);
            return new SlimUser(username, fullName);
        };
    }
}
