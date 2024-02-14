package app.mcorg.project.infrastructure.entities;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.minecraft.Item;
import app.mcorg.project.domain.model.project.ProjectDependency;

import java.util.List;
import java.util.UUID;

public record CountedTaskEntity(UUID id,
                                String name,
                                Priority priority,
                                Integer needed,
                                Integer done,
                                Item item,
                                List<ProjectDependency> projectDependencies) {
}
