package app.mcorg.organizer.presentation.rest.entities.project;

import app.mcorg.organizer.domain.model.minecraft.Item;
import app.mcorg.organizer.domain.model.minecraft.ItemCategory;
import app.mcorg.organizer.domain.model.project.CountedTask;
import app.mcorg.organizer.domain.model.project.Priority;
import org.springframework.lang.NonNull;

import java.util.Optional;

public record CountedTaskResponse(@NonNull String id,
                                  @NonNull String name,
                                  @NonNull Priority priority,
                                  @NonNull int needed,
                                  @NonNull int done,
                                  ItemCategory category) {
    public static CountedTaskResponse from(CountedTask countedTask) {
        return new CountedTaskResponse(
                countedTask.id(),
                countedTask.name(),
                countedTask.priority(),
                countedTask.needed(),
                countedTask.done(),
                Optional.ofNullable(countedTask.item()).map(Item::getCategory).orElse(null)
        );
    }
}
