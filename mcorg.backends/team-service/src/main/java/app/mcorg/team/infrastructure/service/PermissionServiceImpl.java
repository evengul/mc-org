package app.mcorg.team.infrastructure.service;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.team.domain.api.PermissionService;
import app.mcorg.team.domain.api.Permissions;
import app.mcorg.team.domain.api.Teams;
import app.mcorg.team.domain.exceptions.NotFoundException;
import app.mcorg.team.domain.model.permission.UserPermissions;
import app.mcorg.team.domain.model.team.Team;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final UsernameProvider usernameProvider;
    private final Permissions permissions;
    private final Teams teams;

    @Override
    public boolean hasAuthority(String id, Authority authority) {
        UserPermissions userPermissions = getPermissions(usernameProvider.get());

        if (userPermissions.hasAuthority(AuthorityLevel.TEAM, id, authority)) {
            return true;
        }

        String worldId = getTeam(id).getWorldId();

        return userPermissions.hasAuthority(AuthorityLevel.WORLD, worldId, authority);
    }

    @Override
    public boolean hasWorldAuthority(String id, Authority authority) {
        return getPermissions(usernameProvider.get())
                .hasAuthority(AuthorityLevel.WORLD, id, authority);
    }

    private UserPermissions getPermissions(String username) {
        return permissions.get(username)
                .orElseThrow(() -> NotFoundException.user(username));
    }

    private Team getTeam(String id) {
        return teams.get(id)
                .orElseThrow(() -> NotFoundException.team(id));
    }
}
