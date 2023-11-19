package app.mcorg.team.infrastructure.repository.entities;

import app.mcorg.team.domain.model.project.SlimProject;
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
    private String worldId;
    private String name;
    private List<String> users;
    private List<SlimProject> projects;
}
