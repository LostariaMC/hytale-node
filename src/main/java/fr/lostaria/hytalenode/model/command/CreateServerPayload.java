package fr.lostaria.hytalenode.model.command;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateServerPayload(
        String image,
        int port,
        String serverId,
        HytaleAuth hytaleAuth
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HytaleAuth(
            String sessionToken,
            String identityToken,
            String expiresAt
    ) {}
}
