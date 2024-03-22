package app.mcorg.permission.presentation.configuration.security;

import app.mcorg.permission.domain.model.permission.Profile;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Optional;

@RequiredArgsConstructor
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final Converter<Jwt, Collection<GrantedAuthority>> jwtGrantedAuthoritiesConverter;

    @Override
    public AbstractAuthenticationToken convert(@NotNull Jwt source) {
        Collection<GrantedAuthority> authorities = jwtGrantedAuthoritiesConverter.convert(source);
        String principalClaimValue = Optional.ofNullable(source.getClaim("profile"))
                .filter(Profile.class::isInstance)
                .map(Profile.class::cast)
                .map(Profile::minecraftId)
                .orElse(source.getClaimAsString("sub"));
        return new JwtAuthenticationToken(source, authorities, principalClaimValue);
    }
}
