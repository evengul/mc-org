package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.project.Priority;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;

public record AddCountedTaskRequest(@NotEmpty(message = "Task name cannot be empty") String name,
                                    @Nullable Priority priority,
                                    @Positive(message = "A countable task must need a positive amount") Integer needed) { }
