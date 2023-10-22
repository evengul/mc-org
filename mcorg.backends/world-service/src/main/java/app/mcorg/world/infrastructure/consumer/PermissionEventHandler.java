package app.mcorg.world.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.permission.AuthorityAdded;
import app.mcorg.common.event.permission.AuthorityChanged;
import app.mcorg.common.event.permission.AuthorityRemoved;
import app.mcorg.common.event.permission.UserDeleted;
import app.mcorg.world.domain.api.Permissions;
import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.model.permission.UserPermissions;
import app.mcorg.world.domain.model.world.World;
import app.mcorg.world.domain.usecase.world.GetWorldUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class PermissionEventHandler {
    private final Permissions permissions;
    private final GetWorldUseCase getWorldUseCase;
    private final Worlds worlds;
    private final UnitOfWork<World> unitOfWork;

    @Bean
    public Consumer<AuthorityAdded> authorityAddedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.WORLD)) return;

            editPermissions(event.username(), event.name(), event.authorizedId(),
                    (user, world) -> {
                        user.addWorldAuthority(event.authorizedId(), event.authority());
                        world.addUser(user.toSlim());
                    });
        };
    }

    @Bean
    public Consumer<AuthorityChanged> authorityChangedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.WORLD)) return;

            editPermissions(event.username(), event.name(), event.authorizedId(),
                    (user, world) -> {
                        user.changeWorldAuthority(event.authorizedId(), event.authority());
                        world.removeUser(user.username());
                    });
        };
    }

    @Bean
    public Consumer<AuthorityRemoved> authorityRemovedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.WORLD)) return;

            editPermissions(event.username(), event.name(), event.authorizedId(), (user, world) -> {
                user.removeWorldAuthority(event.authorizedId());
                world.removeUser(user.username());
            });
        };
    }

    @Bean
    public Consumer<UserDeleted> userDeletedConsumer() {
        return event -> {
            permissions.delete(event.username());
            worlds.getWorldsWithUser(event.username())
                    .forEach(world -> {
                        world.removeUser(event.username());
                        unitOfWork.add(world);
                    });
        };
    }

    private void editPermissions(String username, String name, String worldId, BiConsumer<UserPermissions, World> edit) {
        World world = getWorldUseCase.execute(new GetWorldUseCase.InputValues(worldId)).world();
        UserPermissions userPermissions = permissions.get(username)
                .orElse(UserPermissions.create(username, name));

        edit.accept(userPermissions, world);
        unitOfWork.add(world);

        permissions.store(userPermissions);
    }
}
