package app.mcorg.permission.infrastructure;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.event.EventDispatcher;
import app.mcorg.common.event.permission.PermissionEvent;
import app.mcorg.common.event.permission.UserDeleted;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.infrastructure.entities.UserPermissionsMapper;
import app.mcorg.permission.infrastructure.repository.MongoUserPermissionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPermissionsUnitOfWork implements UnitOfWork<UserPermissions> {
    private final MongoUserPermissionsRepository repository;
    private final EventDispatcher<PermissionEvent> dispatcher;

    @Override
    public UserPermissions add(UserPermissions aggregateRoot) {
        UserPermissions stored = UserPermissionsMapper.toDomain(
                repository.save(UserPermissionsMapper.toEntity(aggregateRoot)));
        dispatcher.dispatch(aggregateRoot.getDomainEvents());
        return stored;
    }

    @Override
    public void remove(String id) {
        repository.findById(id)
                  .ifPresent(permissions -> {
                      repository.deleteById(permissions.getId());
                      dispatcher.dispatch(new UserDeleted(permissions.getUsername()));
                  });
    }
}
