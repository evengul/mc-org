package app.mcorg.world.presentation.rest.entities.errors;

import org.springframework.lang.NonNull;

public record Validation(@NonNull String fieldName, @NonNull String message) { }
