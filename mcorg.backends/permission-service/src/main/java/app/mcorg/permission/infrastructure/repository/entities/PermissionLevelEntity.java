package app.mcorg.permission.infrastructure.repository.entities;

import app.mcorg.common.domain.model.AuthorityLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document("permission_level")
public class PermissionLevelEntity {
    @Id
    private String id;
    private AuthorityLevel level;
    private PermissionLevelEntity parent;
}
