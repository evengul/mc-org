package app.mcorg.permission.domain.model.permission;

import app.mcorg.common.domain.AggregateRoot;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.permission.AuthorityAdded;
import app.mcorg.common.event.permission.AuthorityChanged;
import app.mcorg.common.event.permission.AuthorityRemoved;
import app.mcorg.common.event.permission.PermissionEvent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.bson.types.ObjectId;

import java.util.*;

import static java.util.Objects.nonNull;

@Getter
@AllArgsConstructor
public class UserPermissions extends AggregateRoot<PermissionEvent> {
    private final String id;
    private final String username;
    private final Map<AuthorityLevel, List<Permission>> permissions;

    public static UserPermissions create(String username) {
        Map<AuthorityLevel, List<Permission>> emptyPermissions = new HashMap<>();
        Arrays.stream(AuthorityLevel.values()).forEach(level -> emptyPermissions.put(level, new ArrayList<>()));
        return new UserPermissions(ObjectId.get().toHexString(), username, emptyPermissions);
    }

    public void addAuthority(AuthorityLevel level, String id, app.mcorg.common.domain.model.Authority authority) {
        this.permissions.get(level).add(new Permission(id, authority));
        this.raiseEvent(new AuthorityAdded(id, username, authority, level));
    }

    public void removeAuthority(AuthorityLevel level, String id) {
        this.permissions.get(level).removeIf(permission -> permission.id().equals(id));
        this.raiseEvent(new AuthorityRemoved(id, username, level));
    }

    public void changeAuthority(AuthorityLevel level, String id, Authority authority) {
        this.permissions.get(level).removeIf(permission -> permission.id().equals(id));
        this.permissions.get(level).add(new Permission(id, authority));
        this.raiseEvent(new AuthorityChanged(id, username, authority, level));
    }

    public boolean hasAuthority(PermissionLevel level, String id, Authority authority) {
        boolean hasAccess = this.permissions.get(level.authorityLevel())
                                            .stream()
                                            .anyMatch(permission -> permission.id().equals(id) && permission.authority()
                                                                                                            .equalsOrHigher(
                                                                                                                    authority));
        return hasAccess ||
                (nonNull(level.parent())
                        && hasAuthority(level.parent(), id, authority));
    }
}
