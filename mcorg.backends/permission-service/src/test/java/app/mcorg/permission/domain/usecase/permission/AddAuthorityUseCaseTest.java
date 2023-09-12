package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.PermissionService;
import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.PermissionLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;
import app.mcorg.permission.infrastructure.MongoContainerTest;
import org.bson.types.ObjectId;
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
public class AddAuthorityUseCaseTest extends MongoContainerTest {

    @Autowired
    private AddAuthorityUseCase useCase;
    @Autowired
    Permissions permissions;

    @Test
    public void addsAuthority() {
        String username = "EVEGUL";
        var input = new AddAuthorityUseCase.InputValues(username, AuthorityLevel.WORLD, ObjectId.get().toHexString(), Authority.OWNER);
        UserPermissions added = useCase.execute(input).permissions();

        assertThat(added.hasAuthority(PermissionLevel.world(input.id()), input.id(), Authority.OWNER)).isTrue();
    }


}
