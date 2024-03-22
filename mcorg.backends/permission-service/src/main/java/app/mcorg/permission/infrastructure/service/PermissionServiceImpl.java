package app.mcorg.permission.infrastructure.service;

import app.mcorg.common.domain.api.UsernameProvider;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.domain.api.PermissionService;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.permission.GetUserPermissionsUseCase;
import app.mcorg.permission.infrastructure.repository.MongoPermissionLevelRepository;
import app.mcorg.permission.infrastructure.repository.entities.PermissionLevelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {
    private final UsernameProvider usernameProvider;
    private final GetUserPermissionsUseCase useCase;
    private final MongoPermissionLevelRepository levelRepository;

    @Override
    public boolean hasAuthority(AuthorityLevel level, String id, Authority authority) {
        return levelRepository.findById(id)
                .filter(foundLevel -> foundLevel.getLevel().equals(level))
                .map(PermissionLevelMapper::toDomain)
                .map(foundLevel -> getPermissions().hasAuthority(foundLevel, id, authority))
                .orElse(false);
    }

    private UserPermissions getPermissions() {
        return useCase.execute(new GetUserPermissionsUseCase.InputValues(usernameProvider.get()))
                .permissions();
    }

}
