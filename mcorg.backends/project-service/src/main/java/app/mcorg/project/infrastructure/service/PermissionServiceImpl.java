package app.mcorg.project.infrastructure.service;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.project.domain.api.PermissionService;
import app.mcorg.project.domain.api.Permissions;
import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.exceptions.NotFoundException;
import app.mcorg.project.domain.model.permission.UserPermissions;
import app.mcorg.project.domain.model.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final UsernameProvider usernameProvider;
    private final Permissions permissions;
    private final Projects projects;

    @Override
    public boolean hasAuthority(String id, Authority authority) {
        UserPermissions userPermissions = getPermissions(usernameProvider.get());

        if (userPermissions.hasAuthority(AuthorityLevel.PROJECT, id, authority)) {
            return true;
        }

        Project project = getProject(id);
        String teamId = project.getTeamId();
        String worldId = project.getWorldId();

        return userPermissions.hasAuthority(AuthorityLevel.TEAM, teamId, authority) ||
                userPermissions.hasAuthority(AuthorityLevel.WORLD, worldId, authority);
    }

    private UserPermissions getPermissions(String username) {
        return permissions.get(username)
                .orElseThrow(() -> NotFoundException.user(username));
    }

    private Project getProject(String id) {
        return projects.get(id)
                .orElseThrow(() -> NotFoundException.project(id));
    }
}
