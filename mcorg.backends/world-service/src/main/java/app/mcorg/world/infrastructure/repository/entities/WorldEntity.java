package app.mcorg.world.infrastructure.repository.entities;

import app.mcorg.world.domain.model.team.SlimTeam;
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
    private List<String> users;
    private List<SlimTeam> teams;
}
