package app.mcorg.project.domain.model.schematic;

import java.util.Map;

public record Schematic(String name, Map<String, Integer> amounts) {
    public static Schematic newInstance(String name, Map<String, Integer> amounts) {
        return new Schematic(name, amounts);
    }
}
