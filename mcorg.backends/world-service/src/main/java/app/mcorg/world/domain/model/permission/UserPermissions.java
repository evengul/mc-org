package app.mcorg.world.domain.model.permission;

import app.mcorg.common.domain.model.Authority;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public record UserPermissions(String id, String username, List<Permission> worldAuthorities) {

    public static UserPermissions create(String username) {
        return new UserPermissions(ObjectId.get().toHexString(), username, new ArrayList<>());
    }

    public void addWorldAuthority(String id, Authority authority) {
        if (this.worldAuthorities.stream().noneMatch(existing -> existing.id().equals(id))) {
            this.worldAuthorities.add(new Permission(id, authority));
        }
    }

    public void removeWorldAuthority(String id) {
        this.worldAuthorities.removeIf(authority -> authority.id().equals(id));
    }

    public void changeWorldAuthority(String id, Authority authority) {
        this.removeWorldAuthority(id);
        this.addWorldAuthority(id, authority);
    }

    public boolean hasAuthority(String id, Authority authority) {
        return this.worldAuthorities.stream()
                .anyMatch(existing -> existing.id().equals(id)
                        && existing.authority().equalsOrHigher(authority));
    }
}
