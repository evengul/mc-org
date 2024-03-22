package app.mcorg.project.presentation.configuration.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Optional;

@Configuration
@EnableWebSecurity
public class WebSecurityConfiguration {

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
                .exceptionHandling(handler -> handler.authenticationEntryPoint((a, b, cause) -> {
                    throw new RuntimeException("Not signed in", cause);
                }))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth -> oauth.bearerTokenResolver(bearerTokenResolver()).jwt(jwt -> jwt.decoder(new JwtDecoder() {
                    @Override
                    public Jwt decode(String token) throws JwtException {
                        return null;
                    }
                })))
                .build();
    }
}
