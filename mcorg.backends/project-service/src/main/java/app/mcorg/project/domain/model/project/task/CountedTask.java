package app.mcorg.project.domain.model.project.task;

import app.mcorg.common.domain.model.Priority;
import app.mcorg.project.domain.model.minecraft.Item;
import app.mcorg.project.domain.model.project.ProjectDependency;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
public class CountedTask extends Task implements Comparable<CountedTask> {
    private final Item item;
    private int needed;
    private int done;

    public CountedTask(UUID id, String name, Priority priority, int needed, int done, Item item,
                       List<ProjectDependency> dependencies) {
        super(id, name, priority, dependencies);
        this.needed = needed;
        this.done = done;
        this.item = item;
    }

    public static CountedTask create(UUID id, String name, Priority priority, int needed, int done,
                                     List<ProjectDependency> dependencies) {
        return new CountedTask(id, name, priority, needed, done, Item.fromName(name).orElse(null), dependencies);
    }

    public static CountedTask create(String name, Priority priority, int needed) {
        return create(null, name, priority, needed, 0, Collections.emptyList());
    }

    public void needsMore(int needed) {
        this.needed = needed;
    }

    public void doneMore(int done) {
        this.done = done;
    }

    @Override
    public boolean isDone() {
        return this.done >= this.needed;
    }

    @Override
    public int compareTo(@NotNull CountedTask o) {
        if (this.isDone()) {
            if (o.isDone()) {
                return Integer.compare(o.needed, this.needed);
            }
            return -1;
        }
        return Integer.compare(o.needed - o.done, this.needed - this.done);
    }
}
