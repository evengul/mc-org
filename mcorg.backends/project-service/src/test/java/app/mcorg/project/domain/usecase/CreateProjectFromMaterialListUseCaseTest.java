package app.mcorg.project.domain.usecase;

import app.mcorg.project.domain.api.SchematicParser;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.model.project.task.CountedTask;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static java.util.Objects.nonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateProjectFromMaterialListUseCaseTest {

    private InputStream file;

    @Before
    public void beforeEach() {
        String filename = "material_list_2021-12-08_19.41.20.txt";
        file = getClass().getClassLoader().getResourceAsStream(filename);
    }

    @After
    public void afterEach() {
        if (nonNull(file)) {
            try {
                file.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void createsProject() {
        // Given
        String projectName = "Project";

        // When
        var outputValues = Project.from(SchematicParser.parseMaterialList(projectName, file));

        // Then

        assertThat(outputValues.getName()).isEqualTo(projectName);
        assertThat(outputValues.doableTasks().toList()).isEmpty();
        assertThat(outputValues.countedTasks().toList()).hasSize(64);

        List<CountedTask> sorted = outputValues.countedTasks()
                .sorted()
                .toList();
        assertThat(sorted.get(0).getNeeded()).isEqualTo(52983);
        assertThat(sorted.get(0).getName()).isEqualTo("Nether Bricks");

    }

    @Test
    public void failsOnEmptyFile() {

    }

    @Test
    public void failsOnMisconfiguredFile() {

    }

    @Test
    public void returnsEmptyProjectFromEmptyList() {

    }
}
