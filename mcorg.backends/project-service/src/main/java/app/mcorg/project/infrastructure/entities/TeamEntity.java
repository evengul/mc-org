package app.mcorg.project.infrastructure.entities;

import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.project.domain.model.project.SlimProject;
import app.mcorg.project.domain.model.world.SlimWorld;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document("team")
public class TeamEntity {
    @Id
    private String id;
    private String name;
    private SlimWorld world;
    private List<SlimProject> projects;
    private List<SlimUser> users;
}
