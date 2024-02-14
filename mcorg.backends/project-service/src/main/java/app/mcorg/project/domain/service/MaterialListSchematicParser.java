package app.mcorg.project.domain.service;

import app.mcorg.project.domain.api.SchematicParser;
import app.mcorg.project.domain.exceptions.SchematicParseException;
import app.mcorg.project.domain.model.schematic.Schematic;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class MaterialListSchematicParser extends SchematicParser {
    private final String regex = "\\+-+\\+-+\\+-+\\++-+\\+\\r*\\n"
            + "\\| Material List for placement '.*' +\\|\\r*\\n"
            + "\\+-+\\+-+\\+-+\\++-+\\+\\r*\\n"
            + "\\| Item +\\| +Total +\\| +Missing +\\| +Available+ \\|\\r*\\n"
            + "\\+-+\\+-+\\+-+\\++-+\\+\\r*\\n"
            + "(\\| ([a-zA-Z ]+)\\| +\\d+ \\| +\\d+ \\| +\\d \\|\\n\\r*)+\\+-+\\+-+\\+-+\\++-+\\+\\r*\\n"
            + "\\| Item +\\| +Total \\| +Missing \\| +Available \\|\n"
            + "\\+-+\\+-+\\+-+\\++-+\\+";
    final Pattern contentPattern = Pattern.compile(regex);

    @Override
    public Schematic parse(String name, InputStream file) {
        Map<String, Integer> amounts = getAmounts(name, file);
        return Schematic.newInstance(name, amounts);
    }

    private Map<String, Integer> getAmounts(String name, InputStream file) {

        String content = validateFile(name, file);

        return Arrays.stream(content.split("\\n"))
                     .skip(5)
                     .filter(line -> !line.contains("+"))
                     .filter(line -> !line.contains("Item"))
                     .map(line -> Arrays.stream(line.split("\\|"))
                                        .map(String::strip)
                                        .toList())
                     .collect(Collectors.toMap(lineCells -> lineCells.get(1),
                                               lineCells -> Integer.parseInt(lineCells.get(2))));
    }

    private String validateFile(String name, InputStream file) {
        try {
            String content = cleanContent(new String(file.readAllBytes(), StandardCharsets.UTF_8));
            if (content.isBlank()) {
                throw new SchematicParseException("File cannot be blank");
            }
            final Matcher matcher = contentPattern.matcher(content);
            if (matcher.find()) {
                if (!matcher.group(0).equals(content)) {
                    throw new SchematicParseException("Invalid content");
                }
            }

            return content;
        } catch (IOException e) {
            throw new SchematicParseException(name);
        }
    }

    private String cleanContent(String content) {
        return content.replaceAll("\\r", "").strip();
    }
}
