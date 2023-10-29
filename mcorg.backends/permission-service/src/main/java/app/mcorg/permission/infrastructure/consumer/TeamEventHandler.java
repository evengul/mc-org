package app.mcorg.permission.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.team.TeamCreated;
import app.mcorg.common.event.team.TeamDeleted;
import app.mcorg.permission.domain.model.permission.PermissionLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.domain.usecase.permission.AddAuthorityUseCase;
import app.mcorg.permission.infrastructure.entities.PermissionLevelMapper;
import app.mcorg.permission.infrastructure.entities.UserPermissionsMapper;
import app.mcorg.permission.infrastructure.repository.MongoPermissionLevelRepository;
import app.mcorg.permission.infrastructure.repository.MongoUserPermissionsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class TeamEventHandler {
    private final MongoPermissionLevelRepository repository;

    @Bean
    public Consumer<TeamCreated> handleTeamCreated(AddAuthorityUseCase authorityUseCase) {
        return event -> {
            PermissionLevel level = PermissionLevel.team(event.id(), event.worldId());
            repository.save(PermissionLevelMapper.toEntity(level));
            authorityUseCase.execute(
                    new AddAuthorityUseCase.InputValues(event.creator(), AuthorityLevel.TEAM, event.id(),
                            Authority.OWNER));
        };
    }

    @Bean
    public Consumer<TeamDeleted> handleTeamDeleted(UnitOfWork<UserPermissions> unitOfWork,
                                                   MongoUserPermissionsRepository userRepository) {
        return event -> {
            repository.deleteById(event.id());
            userRepository.findAllByPermissions_LevelAndPermissions_Id(AuthorityLevel.TEAM, event.id())
                    .stream()
                    .map(UserPermissionsMapper::toDomain)
                    .forEach(permissions -> {
                        permissions.removeAuthority(AuthorityLevel.TEAM, event.id());
                        unitOfWork.add(permissions);
                    });
        };
    }
}
