package app.mcorg.permission.infrastructure.service.entities;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Xsts401Response(@JsonProperty("Identity") String identity,
                              @JsonProperty("XErr") long xErr,
                              @JsonProperty("Message") String message,
                              @JsonProperty("Redirect") String redirect) {
    public String errorFromCode() {
        if (xErr == 2148916233L) return "error.account.xbox.not-found";
        if (xErr == 2148916235L) return "error.account.xbox.banned";
        if (xErr == 2148916236L || xErr == 2148916237L || xErr == 2148916238L)
            return "error.account.xbox.needs-adult-verification";
        return "error.account.xbox.unknown";
    }
}
