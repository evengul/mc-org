package app.mcorg.permission.presentation.configuration.security;

import app.mcorg.permission.domain.api.MyProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Objects;

@RequiredArgsConstructor
public class CustomJwtDecoder implements JwtDecoder {

    private final MyProfile myProfile;

    @Override
    public Jwt decode(String token) throws JwtException {
        String decoded = getToken(token);
        Jwt jwt = JwtDecoders.fromIssuerLocation("https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0")
                .decode(decoded);

        return Jwt.withTokenValue(jwt.getTokenValue())
                .audience(jwt.getAudience())
                .claims(map -> {
                    map.putAll(jwt.getClaims());
                    map.put("profile", myProfile.get(token));
                })
                .headers(map -> map.putAll(jwt.getHeaders()))
                .expiresAt(jwt.getExpiresAt())
                .issuedAt(jwt.getIssuedAt())
                .subject(jwt.getSubject())
                .issuer("https://login.microsoftonline.com/9188040d-6c67-4c5b-b112-36a304b66dad/v2.0")
                .build();
    }

    private String getToken(String userToken) {
        return Objects.requireNonNull(RestClient.builder()
                .baseUrl("https://login.microsoftonline.com/consumers/oauth2/v2.0")
                .defaultHeader("content-type", "application/x-www-form-urlencoded")
                .build()
                .post()
                .uri("/token")
                .body(Body.create(userToken).asMap())
                .retrieve()
                .toEntity(Response.class)
                .getBody())
                .access_token;
    }

    private record Body(String grantType, String clientId, String clientSecret, String assertion, String scope,
                        String request_token_use) {
        private static Body create(String userToken) {
            return new Body(
                    "client_credentials",
                    "49447253-46f6-4dee-8d48-46415073729f",
                    "guu8Q~i5BWfVRY6VYPr3F-YF~hSr.mh0waOeAdkY",
                    userToken,
                    "api://49447253-46f6-4dee-8d48-46415073729f/.default",
                    "on_behalf_of"
            );
        }

        private MultiValueMap<String, String> asMap() {
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("grant_type", grantType);
            map.add("client_id", clientId);
            map.add("client_secret", clientSecret);
            map.add("assertion", assertion);
            map.add("scope", scope);
            map.add("request_token_use", request_token_use);
            return map;
        }
    }

    private record Response(String token_type, int expires_in, int ext_expires_in, String access_token) {
    }
}
