package app.mcorg.project.infrastructure.entities;

import app.mcorg.project.domain.model.permission.Permission;
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
@Document("permission")
public class UserPermissionEntity {
    @Id
    String id;
    String username;
    List<Permission> worldPermissions;
    List<Permission> teamPermissions;
}
