package app.mcorg.permission.infrastructure.service.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TokenResponse(@JsonProperty("IssueInstant") String issueInstant,
                            @JsonProperty("NotAfter") String notAfter,
                            @JsonProperty("Token") String token,
                            @JsonProperty("DisplayClaims") DisplayClaims displayClaims) {

    public String userHash() {
        return this.displayClaims.xui[0].uhs;
    }

    private record DisplayClaims(@JsonProperty("xui") Uhs[] xui) {
        private record Uhs(@JsonProperty("uhs") String uhs) {
        }
    }
}
