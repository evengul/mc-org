package app.mcorg.team.domain.model.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record UserPermissions(String id, String username, Map<AuthorityLevel, List<Permission>> permissions) {

    public static UserPermissions create(String username) {
        return new UserPermissions(ObjectId.get().toHexString(), username, Map.of(AuthorityLevel.WORLD, new ArrayList<>(), AuthorityLevel.TEAM, new ArrayList<>()));
    }

    public void addAuthority(AuthorityLevel level, String id, Authority authority) {
        if (permissionsOnLevel(level).stream().noneMatch(permission -> permission.id().equals(id))) {
            permissionsOnLevel(level).add(new Permission(id, authority));
        }
    }

    public void removeAuthority(AuthorityLevel level, String id) {
        permissionsOnLevel(level).removeIf(permission -> permission.id().equals(id));
    }

    public void changeAuthority(AuthorityLevel level, String id, Authority authority) {
        this.removeAuthority(level, id);
        this.addAuthority(level, id, authority);
    }

    public boolean hasAuthority(AuthorityLevel level, String id, Authority authority) {
        return permissionsOnLevel(level).stream()
                .anyMatch(existing -> existing.id().equals(id)
                        && existing.authority().equalsOrHigher(authority));
    }

    private List<Permission> permissionsOnLevel(AuthorityLevel level) {
        return Optional.of(this.permissions.get(level)).orElse(new ArrayList<>());
    }
}
