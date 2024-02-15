package app.mcorg.project.application.consumer;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.common.event.permission.AuthorityAdded;
import app.mcorg.common.event.permission.AuthorityChanged;
import app.mcorg.common.event.permission.AuthorityRemoved;
import app.mcorg.common.event.permission.UserDeleted;
import app.mcorg.project.domain.api.Permissions;
import app.mcorg.project.domain.api.Projects;
import app.mcorg.project.domain.model.permission.UserPermissions;
import app.mcorg.project.domain.model.project.Project;
import app.mcorg.project.domain.usecase.project.GetProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Configuration
@RequiredArgsConstructor
public class PermissionEventHandler {
    private final Permissions permissions;
    private final GetProjectUseCase getProjectUseCase;
    private final Projects projects;
    private final UnitOfWork<Project> unitOfWork;

    private final static List<AuthorityLevel> acceptedLevels = List.of(AuthorityLevel.WORLD, AuthorityLevel.TEAM,
                                                                       AuthorityLevel.PROJECT);

    @Bean
    public Consumer<AuthorityAdded> authorityAddedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) {
                return;
            }

            editPermissions(event.username(), event.authorizedId(),
                            (user, project) -> {
                                user.addAuthority(event.level(), event.authorizedId(), event.authority());
                                project.addUser(user.username());
                            });
        };
    }

    @Bean
    public Consumer<AuthorityChanged> authorityChangedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) {
                return;
            }

            editPermissions(event.username(), event.authorizedId(),
                            (user, project) -> user.changeAuthority(event.level(), event.authorizedId(),
                                                                    event.authority()));
        };
    }

    @Bean
    public Consumer<AuthorityRemoved> authorityRemovedConsumer() {
        return event -> {
            if (!acceptedLevels.contains(event.level())) {
                return;
            }

            editPermissions(event.username(), event.authorizedId(),
                            (user, project) -> {
                                user.removeAuthority(event.level(), event.authorizedId());
                                project.removeUser(user.username());
                            });
        };
    }

    @Bean
    public Consumer<UserDeleted> userDeletedConsumer() {
        return event -> {
            permissions.delete(event.username());
            projects.getProjectsWithUser(event.username())
                    .forEach(project -> {
                        project.removeUser(event.username());
                        unitOfWork.add(project);
                    });
        };
    }

    private void editPermissions(String username, String projectId, BiConsumer<UserPermissions, Project> edit) {
        Project project = getProjectUseCase.execute(new GetProjectUseCase.InputValues(projectId)).project();
        UserPermissions userPermissions = permissions.get(username)
                                                     .orElse(UserPermissions.create(username));

        edit.accept(userPermissions, project);

        unitOfWork.add(project);
        permissions.persist(userPermissions);
    }
}
