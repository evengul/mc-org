package app.mcorg.permission.presentation.configuration.security;

import app.mcorg.permission.domain.api.MyProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class WebSecurityConfiguration {

    private final MyProfile myProfile;

    public BearerTokenResolver bearerTokenResolver() {
        return request -> Optional.ofNullable(request.getHeader("Authorization"))
                .map(token -> token.replaceAll("Bearer ", ""))
                .orElse(null);
    }

    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("HEAD", "GET", "PUT", "PATCH", "POST", "DELETE"));
        configuration.setAllowCredentials(true);
        configuration.addAllowedHeader("*");
        configuration.addExposedHeader("Location");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .exceptionHandling(handler -> handler.authenticationEntryPoint((request, response, authException) -> {
                    throw new RuntimeException("Not signed in", authException);
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.bearerTokenResolver(this.bearerTokenResolver())
                                .jwt(jwt -> jwt.decoder(new CustomJwtDecoder(myProfile))))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .build();
    }

}
