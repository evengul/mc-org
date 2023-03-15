package app.mcorg.organizer.domain.api;

import app.mcorg.organizer.domain.model.schematic.Schematic;
import app.mcorg.organizer.domain.usecase.schematic.MaterialListSchematicParser;

import java.io.IOException;
import java.io.InputStream;

public abstract class SchematicParser {
    protected abstract Schematic parse(String name, InputStream file) throws IOException;

    public static Schematic parseMaterialList(String name, InputStream file) throws IOException {
        return new MaterialListSchematicParser()
                .parse(name, file);
    }
}
