package app.mcorg.world.domain.model.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.SlimUser;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public record UserPermissions(String id, String username, String name, List<Permission> worldAuthorities) {

    public static UserPermissions create(String username, String name) {
        return new UserPermissions(ObjectId.get().toHexString(), username, name, new ArrayList<>());
    }

    public SlimUser toSlim() {
        return new SlimUser(this.username, name);
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
