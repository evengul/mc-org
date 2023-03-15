package app.mcorg.organizer.presentation.rest.entities.project;

import jakarta.validation.constraints.NotEmpty;

public record CreateProjectRequest(@NotEmpty(message = "Project name cannot be empty") String name) { }
