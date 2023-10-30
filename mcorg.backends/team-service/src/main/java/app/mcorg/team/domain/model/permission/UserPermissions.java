package app.mcorg.team.domain.model.permission;

import app.mcorg.common.domain.model.Authority;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public record UserPermissions(String id, String username, List<Permission> teamPermissions) {

    public static UserPermissions create(String username) {
        return new UserPermissions(ObjectId.get().toHexString(), username, new ArrayList<>());
    }

    public void addTeamAuthority(String id, Authority authority) {
        if (this.teamPermissions.stream().noneMatch(existing -> existing.id().equals(id))) {
            this.teamPermissions.add(new Permission(id, authority));
        }
    }

    public void removeWorldAuthority(String id) {
        this.teamPermissions.removeIf(authority -> authority.id().equals(id));
    }

    public void changeTeamAuthority(String id, Authority authority) {
        this.removeWorldAuthority(id);
        this.addTeamAuthority(id, authority);
    }

    public boolean hasAuthority(String id, Authority authority) {
        return this.teamPermissions.stream()
                .anyMatch(existing -> existing.id().equals(id)
                        && existing.authority().equalsOrHigher(authority));
    }
}
