package app.mcorg.project.domain.api;

import app.mcorg.project.domain.model.schematic.Schematic;
import app.mcorg.project.domain.service.MaterialListSchematicParser;

import java.io.IOException;
import java.io.InputStream;

public abstract class SchematicParser {
    @SuppressWarnings("unused")
    protected abstract Schematic parse(String name, InputStream file) throws IOException;

    public static Schematic parseMaterialList(String name, InputStream file) {
        return new MaterialListSchematicParser()
                .parse(name, file);
    }
}
