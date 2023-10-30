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

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class PermissionEventHandler {
    private final Permissions permissions;
    private final GetTeamUseCase getTeamUseCase;
    private final Teams teams;
    private final UnitOfWork<Team> unitOfWork;

    @Bean
    public Consumer<AuthorityAdded> authorityAddedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.TEAM)) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, team) -> {
                        user.addTeamAuthority(event.authorizedId(), event.authority());
                        team.addUser(user.username());
                    });
        };
    }

    @Bean
    public Consumer<AuthorityChanged> authorityChangedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.TEAM)) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, _) -> user.changeTeamAuthority(event.authorizedId(), event.authority()));
        };
    }

    @Bean
    public Consumer<AuthorityRemoved> authorityRemovedConsumer() {
        return event -> {
            if (!event.level().equals(AuthorityLevel.TEAM)) return;

            editPermissions(event.username(), event.authorizedId(),
                    (user, team) -> {
                        user.removeWorldAuthority(event.authorizedId());
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
