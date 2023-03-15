package app.mcorg.organizer.presentation.rest.entities.errors;

import org.springframework.lang.NonNull;

public record Validation(@NonNull String fieldName, @NonNull String message) { }
