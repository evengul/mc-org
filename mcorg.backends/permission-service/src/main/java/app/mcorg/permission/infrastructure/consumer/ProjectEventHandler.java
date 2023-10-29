package app.mcorg.permission.infrastructure.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.project.ProjectCreated;
import app.mcorg.common.event.project.ProjectDeleted;
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
public class ProjectEventHandler {
    private final MongoPermissionLevelRepository repository;

    @Bean
    public Consumer<ProjectCreated> handleProjectCreated(AddAuthorityUseCase useCase) {
        return event -> {
            PermissionLevel level = PermissionLevel.project(event.id(), event.teamId(), event.worldId());
            repository.save(PermissionLevelMapper.toEntity(level));
            useCase.execute(
                    new AddAuthorityUseCase.InputValues(event.creator(), AuthorityLevel.PROJECT, event.id(),
                            Authority.OWNER));
        };
    }

    @Bean
    public Consumer<ProjectDeleted> handleProjectDeleted(UnitOfWork<UserPermissions> unitOfWork,
                                                         MongoUserPermissionsRepository userRepository) {
        return event -> {
            repository.deleteById(event.id());
            userRepository.findAllByPermissions_LevelAndPermissions_Id(AuthorityLevel.PROJECT, event.id())
                    .stream()
                    .map(UserPermissionsMapper::toDomain)
                    .forEach(permission -> {
                        permission.removeAuthority(AuthorityLevel.PROJECT, event.id());
                        unitOfWork.add(permission);
                    });
        };
    }
}
