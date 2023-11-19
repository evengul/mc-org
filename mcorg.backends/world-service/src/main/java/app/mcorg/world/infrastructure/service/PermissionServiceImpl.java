package app.mcorg.world.infrastructure.service;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.world.domain.api.PermissionService;
import app.mcorg.world.domain.api.Permissions;
import app.mcorg.world.domain.exceptions.NotFoundException;
import app.mcorg.world.domain.model.permission.UserPermissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final UsernameProvider usernameProvider;
    private final Permissions permissions;

    @Override
    public boolean hasAuthority(String id, Authority authority) {
        return getPermissions(usernameProvider.get())
                .hasAuthority(id, authority);
    }

    private UserPermissions getPermissions(String username) {
        return permissions.get(username)
                .orElseThrow(() -> NotFoundException.user(username));
    }
}
