package app.mcorg.world.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.permission.AuthorityAdded;
import app.mcorg.common.event.permission.AuthorityChanged;
import app.mcorg.common.event.permission.AuthorityRemoved;
import app.mcorg.common.event.permission.UserDeleted;
import app.mcorg.world.MongoContainerTest;
import app.mcorg.world.WorldService;
import app.mcorg.world.domain.api.Permissions;
import app.mcorg.world.domain.model.permission.UserPermissions;
import app.mcorg.world.domain.model.world.World;
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
@SpringBootTest(classes = WorldService.class)
public class PermissionEventHandlerTest extends MongoContainerTest {
    @Autowired
    UnitOfWork<World> unitOfWork;
    @Autowired
    private Consumer<AuthorityAdded> authorityAddedConsumer;

    @Autowired
    private Consumer<AuthorityChanged> authorityChangedConsumer;

    @Autowired
    private Consumer<AuthorityRemoved> authorityRemovedConsumer;

    @Autowired
    private Consumer<UserDeleted> userDeletedConsumer;

    @Autowired
    private Permissions permissions;

    private final String username = "EVEGUL";

    @Test
    public void handleAddAuthority() {
        // Given
        AuthorityAdded event = new AuthorityAdded(createWorldAndGetId(), username, Authority.OWNER, AuthorityLevel.WORLD);

        // When
        authorityAddedConsumer.accept(event);

        // Then
        Optional<UserPermissions> userPermissions = permissions.get(username);
        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(event.authorizedId(), event.authority())).isTrue();
    }

    @Test
    public void ignoresAddTeamAuthority() {
        // Given
        AuthorityAdded event = new AuthorityAdded(createWorldAndGetId(), username, Authority.OWNER, AuthorityLevel.TEAM);

        // When
        authorityAddedConsumer.accept(event);

        // Then
        Optional<UserPermissions> userPermissions = permissions.get(username);
        assertThat(userPermissions).isEmpty();
    }

    @Test
    public void handleChangeAuthority() {
        // Given
        AuthorityAdded createEvent = new AuthorityAdded(createWorldAndGetId(), username, Authority.OWNER, AuthorityLevel.WORLD);
        AuthorityChanged event = new AuthorityChanged(createEvent.authorizedId(), username, Authority.ADMIN, AuthorityLevel.WORLD);

        // When
        authorityAddedConsumer.accept(createEvent);
        authorityChangedConsumer.accept(event);

        // Then
        Optional<UserPermissions> userPermissions = permissions.get(username);
        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(event.authorizedId(), event.authority())).isTrue();
    }

    @Test
    public void handleRemoveAuthority() {
        // Given
        AuthorityAdded createEvent = new AuthorityAdded(createWorldAndGetId(), username, Authority.OWNER, AuthorityLevel.WORLD);
        AuthorityRemoved event = new AuthorityRemoved(createEvent.authorizedId(), username, AuthorityLevel.WORLD);

        // When
        authorityAddedConsumer.accept(createEvent);
        authorityRemovedConsumer.accept(event);

        // Then
        Optional<UserPermissions> userPermissions = permissions.get(username);
        assertThat(userPermissions).isPresent();
        assertThat(userPermissions.get().hasAuthority(createEvent.authorizedId(), createEvent.authority())).isFalse();
    }

    @Test
    public void handleDeleteUser() {
        // Given
        AuthorityAdded createEvent = new AuthorityAdded(createWorldAndGetId(), username, Authority.OWNER, AuthorityLevel.WORLD);
        UserDeleted event = new UserDeleted(username);

        // When
        authorityAddedConsumer.accept(createEvent);
        userDeletedConsumer.accept(event);

        // Then
        Optional<UserPermissions> userPermissions = permissions.get(username);
        assertThat(userPermissions).isEmpty();
    }

    private String createWorldAndGetId() {
        return unitOfWork.add(World.create("WORLD", username))
                .getId();
    }
}
