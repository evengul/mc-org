package app.mcorg.permission.infrastructure.service;

import app.mcorg.permission.domain.api.MyProfile;
import app.mcorg.permission.domain.exceptions.AccessDeniedException;
import app.mcorg.permission.domain.model.permission.Profile;
import app.mcorg.permission.infrastructure.service.entities.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class MyProfileService implements MyProfile {

    @Override
    public Profile get() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken auth) {
            return auth.getToken().getClaim("profile");
        }
        throw new AccessDeniedException();
    }

    @Override
    public Profile get(String microsoftAccessToken) {
        String minecraftToken = getMinecraftJwt(microsoftAccessToken);

        MinecraftProfileResponse profile = getMinecraftProfile(minecraftToken);

        return new Profile(profile.id(), profile.name());
    }

    @Override
    public String getMinecraftJwt(String microsoftAccessToken) {
        TokenResponse xboxProfile = getXboxProfile(microsoftAccessToken);
        String xboxToken = xboxProfile.token();
        String userHash = xboxProfile.userHash();

        String xstsToken = getXstsToken(xboxToken).token();
        return getMinecraftToken(xstsToken, userHash).access_token();
    }

    private TokenResponse getXboxProfile(String microsoftAccessToken) {
        return RestClient.builder()
                .baseUrl("https://user.auth.xboxlive.com")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build()
                .post()
                .uri("/user/authenticate")
                .body(XboxProfileRequest.create(microsoftAccessToken))
                .retrieve()
                .toEntity(TokenResponse.class)
                .getBody();
    }

    private TokenResponse getXstsToken(String xboxToken) {
        return RestClient.builder()
                .baseUrl("https://xsts.auth.xboxlive.com")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build()
                .post()
                .uri("/xsts/authorize")
                .body(XstsRequest.create(xboxToken))
                .retrieve()
                .toEntity(TokenResponse.class)
                .getBody();
    }

    private MinecraftTokenResponse getMinecraftToken(String xstsToken, String userHash) {
        return RestClient.builder()
                .baseUrl("https://api.minecraftservices.com")
                .defaultHeader("Content-Type", "application/json")
                .defaultHeader("Accept", "application/json")
                .build()
                .post()
                .uri("/authentication/login_with_xbox")
                .body(MinecraftRequest.create(userHash, xstsToken))
                .retrieve()
                .toEntity(MinecraftTokenResponse.class)
                .getBody();
    }

    private MinecraftProfileResponse getMinecraftProfile(String minecraftToken) {
        return RestClient.builder()
                .baseUrl("https://api.minecraftservices.com")
                .defaultHeader("Authorization", "Bearer " + minecraftToken)
                .build()
                .get()
                .uri("/minecraft/profile")
                .retrieve()
                .toEntity(MinecraftProfileResponse.class)
                .getBody();
    }
}
