package app.mcorg.permission.infrastructure.service.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public record XstsRequest(@JsonProperty("Properties") Properties properties,
                          @JsonProperty("RelyingParty") String relyingParty,
                          @JsonProperty("TokenType") String tokenType) {

    public static XstsRequest create(String xboxToken) {
        return new XstsRequest(
                new Properties("RETAIL", new String[]{xboxToken}),
                "rp://api.minecraftservices.com/",
                "JWT"
        );
    }

    private record Properties(@JsonProperty("SandboxId") String sandboxId,
                              @JsonProperty("UserTokens") String[] userTokens
    ) {
    }
}
