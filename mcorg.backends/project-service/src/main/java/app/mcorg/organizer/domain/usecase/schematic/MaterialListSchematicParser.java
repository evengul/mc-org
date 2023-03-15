package app.mcorg.organizer.domain.usecase.schematic;

import app.mcorg.organizer.domain.api.SchematicParser;
import app.mcorg.organizer.domain.model.schematic.Schematic;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class MaterialListSchematicParser extends SchematicParser {
    @Override
    public Schematic parse(String name, InputStream file) throws IOException {
        Map<String, Integer> amounts = getAmounts(file);
        return Schematic.newInstance(name, amounts);
    }

    private Map<String, Integer> getAmounts(InputStream file) throws IOException {
        String content = new String(file.readAllBytes(), StandardCharsets.UTF_8);
        return Arrays.stream(content.split(System.lineSeparator()))
                .skip(5)
                .filter(line -> !line.contains("+"))
                .filter(line -> !line.contains("Item"))
                .map(line -> Arrays.stream(line.split("\\|"))
                        .map(String::strip)
                        .toList())
                .collect(Collectors.toMap(lineCells -> lineCells.get(1), lineCells -> Integer.parseInt(lineCells.get(2))));
    }
}
