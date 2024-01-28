package app.mcorg.team.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.permission.AuthorityAdded;
import app.mcorg.common.event.permission.AuthorityChanged;
import app.mcorg.common.event.permission.AuthorityRemoved;
import app.mcorg.common.event.permission.UserDeleted;
import app.mcorg.team.domain.api.Permissions;
import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.model.permission.UserPermissions;
import app.mcorg.team.domain.model.team.Team;
import app.mcorg.team.domain.usecase.team.GetTeamUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class PermissionEventHandler {
    private final Permissions permissions;
    private final GetTeamUseCase getTeamUseCase;
    private final Teams teams;
    private final UnitOfWork<Team> unitOfWork;

    private final static List<AuthorityLevel> acceptedLevels = List.of(AuthorityLevel.WORLD, AuthorityLevel.TEAM);

    @Bean
    public Consumer<AuthorityAdded> authorityAddedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, team) -> {
                        user.addAuthority(event.level(), event.authorizedId(), event.authority());
                        team.addUser(user.username());
                    });
        };
    }

    @Bean
    public Consumer<AuthorityChanged> authorityChangedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, team) -> user.changeAuthority(event.level(), event.authorizedId(), event.authority()));
        };
    }

    @Bean
    public Consumer<AuthorityRemoved> authorityRemovedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, team) -> {
                        user.removeAuthority(event.level(), event.authorizedId());
                        team.removeUser(user.username());
                    });
        };
    }

    @Bean
    public Consumer<UserDeleted> userDeletedConsumer() {
        return event -> {
            permissions.delete(event.username());
            teams.getTeamsWithUser(event.username())
                    .forEach(team -> {
                        team.removeUser(event.username());
                        unitOfWork.add(team);
                    });
        };
    }

    private void editPermissions(String username, String teamId, BiConsumer<UserPermissions, Team> edit) {
        Team team = getTeamUseCase.execute(new GetTeamUseCase.InputValues(teamId)).team();
        UserPermissions userPermissions = permissions.get(username)
                .orElse(UserPermissions.create(username));

        edit.accept(userPermissions, team);

        unitOfWork.add(team);
        permissions.store(userPermissions);
    }
}
