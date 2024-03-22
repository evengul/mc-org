package app.mcorg.permission.infrastructure.service.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public record XboxProfileRequest(@JsonProperty("Properties") Properties properties,
                                 @JsonProperty("RelyingParty") String relyingParty,
                                 @JsonProperty("TokenType") String tokenType) {

    public static XboxProfileRequest create(String accessToken) {
        return new XboxProfileRequest(
                new Properties(
                        "RPS",
                        "user.auth.xboxlive.com",
                        "d=" + accessToken
                ),
                "http://auth.xboxlive.com",
                "JWT"
        );
    }

    private record Properties(@JsonProperty("AuthMethod") String authMethod,
                              @JsonProperty("SiteName") String siteName,
                              @JsonProperty("RpsTicket") String rpsTicket) {
    }
}
