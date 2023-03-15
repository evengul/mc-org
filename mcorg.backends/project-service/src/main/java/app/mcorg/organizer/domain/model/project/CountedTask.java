package app.mcorg.organizer.domain.model.project;

import app.mcorg.organizer.domain.model.minecraft.Item;
import jakarta.validation.constraints.NotNull;

public record CountedTask(String id, String name, Priority priority, int needed, int done, Item item) implements Comparable<CountedTask> {

    public static CountedTask newInstance(String id, String name, Priority priority, int needed, int done) {
        return new CountedTask(id, name, priority, needed, done, Item.fromName(name).orElse(null));
    }

    public static CountedTask newInstance(String name, Priority priority, int needed) {
        return newInstance(null, name, priority, needed, 0);
    }

    public CountedTask rename(String name) {
        return new CountedTask(
                this.id,
                name,
                this.priority,
                this.needed,
                this.done,
                this.item
        );
    }

    public CountedTask reprioritize(Priority priority) {
        return new CountedTask(
                this.id,
                this.name,
                priority,
                this.needed,
                this.done,
                this.item
        );
    }

    public CountedTask needsMore(int needed) {
        return new CountedTask(
                this.id,
                this.name,
                this.priority,
                needed,
                this.done,
                this.item
        );
    }

    public CountedTask doneMore(int done) {
        return new CountedTask(
                this.id,
                this.name,
                this.priority,
                this.needed,
                done,
                this.item
        );
    }

    public boolean isDone() {
        return this.done >= this.needed;
    }

    @Override
    public int compareTo(CountedTask o) {
        if(this.isDone()) {
            if(o.isDone()) {
                return Integer.compare(o.needed, this.needed);
            }
            return -1;
        }
        return Integer.compare(o.needed - o.done, this.needed - this.done);
    }
}
