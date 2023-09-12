package app.mcorg.permission.infrastructure.consumer;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.common.event.project.ProjectCreated;
import app.mcorg.common.event.project.ProjectDeleted;
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
public class ProjectEventHandlerTest extends MongoContainerTest {

    private final String username = "EVEGUL";

    @Autowired
    private Consumer<ProjectCreated> handleProjectCreated;
    @Autowired
    private Consumer<ProjectDeleted> handleProjectDeleted;
    @Autowired
    private MongoPermissionLevelRepository repository;
    @Autowired
    private Permissions permissions;

    @Test
    public void handleCreateProject() {
        // Given
        ProjectCreated event = new ProjectCreated(ObjectId.get().toHexString(), ObjectId.get().toHexString(), ObjectId.get().toHexString(), "ProjectName", new SlimUser(username, "Even"));

        // When
        handleProjectCreated.accept(event);

        Optional<UserPermissions> userPermissions = permissions.get(username);
        Optional<PermissionLevelEntity> levelEntity = repository.findById(event.id());

        // Then
        assertThat(levelEntity).isPresent();
        PermissionLevel projectLevel = PermissionLevelMapper.toDomain(levelEntity.get());
        assertThat(projectLevel.id()).isEqualTo(event.id());
        assertThat(projectLevel.authorityLevel()).isEqualTo(AuthorityLevel.PROJECT);
        assertThat(projectLevel.parent()).isNotNull();
        PermissionLevel teamLevel = projectLevel.parent();
        assertThat(teamLevel.id()).isEqualTo(event.teamId());
        assertThat(teamLevel.authorityLevel()).isEqualTo(AuthorityLevel.TEAM);
        assertThat(teamLevel.parent()).isNotNull();
        PermissionLevel worldLevel = teamLevel.parent();
        assertThat(worldLevel.id()).isEqualTo(event.worldId());
        assertThat(worldLevel.authorityLevel()).isEqualTo(AuthorityLevel.WORLD);
        assertThat(worldLevel.parent()).isNull();

        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(projectLevel, event.id(), Authority.OWNER)).isTrue();
        assertThat(userPermissions.get().hasAuthority(teamLevel, event.id(), Authority.OWNER)).isFalse();
        assertThat(userPermissions.get().hasAuthority(worldLevel, event.id(), Authority.OWNER)).isFalse();
    }

    @Test
    public void handleDeleteProject() {
        // Given
        ProjectCreated initEvent = new ProjectCreated(ObjectId.get().toHexString(), ObjectId.get().toHexString(), ObjectId.get().toHexString(), "ProjectName", new SlimUser(username, "Even"));
        ProjectDeleted event = new ProjectDeleted(initEvent.id());

        // When
        handleProjectCreated.accept(initEvent);
        handleProjectDeleted.accept(event);

        Optional<UserPermissions> userPermissions = permissions.get(username);
        Optional<PermissionLevelEntity> levelEntity = repository.findById(event.id());

        assertThat(levelEntity).isEmpty();
        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(
                PermissionLevel.project(initEvent.id(), initEvent.teamId(), initEvent.worldId()),
                initEvent.id(),
                Authority.OWNER
        )).isFalse();
    }
}
