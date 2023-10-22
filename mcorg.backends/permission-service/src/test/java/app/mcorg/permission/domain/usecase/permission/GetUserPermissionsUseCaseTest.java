package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.permission.MongoContainerTest;
import app.mcorg.permission.PermissionService;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.infrastructure.entities.UserPermissionsMapper;
import app.mcorg.permission.infrastructure.repository.MongoUserPermissionsRepository;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@RunWith(SpringRunner.class)
@SpringBootTest(classes = PermissionService.class)
public class GetUserPermissionsUseCaseTest extends MongoContainerTest {
    @Autowired
    private MongoUserPermissionsRepository repository;

    @Autowired
    GetUserPermissionsUseCase useCase;

    @Test
    public void retrievesExistingUser() {
        String username = "EVEGUL";
        UserPermissions permissions = UserPermissions.create(username);
        repository.save(UserPermissionsMapper.toEntity(permissions));

        UserPermissions retrieved = useCase.execute(new GetUserPermissionsUseCase.InputValues(username))
                .permissions();

        assertThat(retrieved.getUsername()).isEqualTo(username);
    }

    @Test
    public void createsAndReturnsNonExistingUser() {
        String username = "NOT_FOUND";
        UserPermissions retrieved = useCase.execute(new GetUserPermissionsUseCase.InputValues(username))
                .permissions();

        assertThat(retrieved.getUsername()).isEqualTo(username);
    }
}
