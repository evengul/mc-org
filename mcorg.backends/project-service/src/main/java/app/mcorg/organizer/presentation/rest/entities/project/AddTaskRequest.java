package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.project.Priority;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;

public record AddTaskRequest(@NotEmpty(message = "Task name cannot be empty") String name, @Nullable Priority priority) { }
