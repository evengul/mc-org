package app.mcorg.permission.infrastructure.repository.entities;

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
public class UserPermissionsEntity {
    @Id
    private String id;
    private String username;
    private List<PermissionEntity> permissions;
}
