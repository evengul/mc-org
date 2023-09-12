package app.mcorg.permission.infrastructure.consumer;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.common.event.world.WorldCreated;
import app.mcorg.common.event.world.WorldDeleted;
import app.mcorg.permission.PermissionService;
import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.PermissionLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.infrastructure.MongoContainerTest;
import app.mcorg.permission.infrastructure.entities.PermissionLevelEntity;
import app.mcorg.permission.infrastructure.entities.PermissionLevelMapper;
import app.mcorg.permission.infrastructure.repository.MongoPermissionLevelRepository;
import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PermissionService.class)
public class WorldEventHandlerTest extends MongoContainerTest {
    private final String username = "EVEGUL";

    @Autowired
    private Consumer<WorldCreated> handleWorldCreated;
    @Autowired
    private Consumer<WorldDeleted> handleWorldDeleted;
    @Autowired
    private MongoPermissionLevelRepository repository;
    @Autowired
    private Permissions permissions;

    @Test
    public void handleCreateProject() {
        // Given
        WorldCreated event = new WorldCreated(ObjectId.get().toHexString(), "WorldName", new SlimUser(username, "Even"));

        // When
        handleWorldCreated.accept(event);

        Optional<UserPermissions> userPermissions = permissions.get(username);
        Optional<PermissionLevelEntity> levelEntity = repository.findById(event.id());

        // Then
        assertThat(levelEntity).isPresent();
        PermissionLevel worldLevel = PermissionLevelMapper.toDomain(levelEntity.get());
        assertThat(worldLevel.id()).isEqualTo(event.id());
        assertThat(worldLevel.authorityLevel()).isEqualTo(AuthorityLevel.WORLD);
        assertThat(worldLevel.parent()).isNull();

        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(worldLevel, event.id(), Authority.OWNER)).isTrue();
    }

    @Test
    public void handleDeleteProject() {
        // Given
        WorldCreated initEvent = new WorldCreated(ObjectId.get().toHexString(), "WorldName", new SlimUser(username, "Even"));
        WorldDeleted event = new WorldDeleted(initEvent.id());

        // When
        handleWorldCreated.accept(initEvent);
        handleWorldDeleted.accept(event);

        Optional<UserPermissions> userPermissions = permissions.get(username);
        Optional<PermissionLevelEntity> levelEntity = repository.findById(event.id());

        assertThat(levelEntity).isEmpty();
        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(
                PermissionLevel.world(initEvent.id()),
                initEvent.id(),
                Authority.OWNER
        )).isFalse();
    }
}
