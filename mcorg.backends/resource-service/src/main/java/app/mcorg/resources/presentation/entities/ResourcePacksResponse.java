package app.mcorg.resources.presentation.entities;

import org.springframework.lang.NonNull;

import java.util.List;

public record ResourcePacksResponse(@NonNull List<ResourcePackResponse> resourcePacks) {
}
