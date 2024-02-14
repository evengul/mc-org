package app.mcorg.project.infrastructure.entities;

import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.project.domain.model.team.SlimTeam;
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
@Document("world")
public class WorldEntity {
    @Id
    private String id;
    private String name;
    private List<SlimTeam> teams;
    private List<SlimUser> users;
}
