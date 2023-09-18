package app.mcorg.permission.domain.usecase.permission;

import app.mcorg.common.domain.model.Authority;
import app.mcorg.common.domain.model.AuthorityLevel;
import app.mcorg.permission.MongoContainerTest;
import app.mcorg.permission.PermissionService;
import app.mcorg.permission.domain.api.Permissions;
import app.mcorg.permission.domain.model.permission.PermissionLevel;
import app.mcorg.permission.domain.model.permission.UserPermissions;
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
public class ChangeAuthorityUseCaseTest extends MongoContainerTest {

    @Autowired
    private ChangeAuthorityUseCase useCase;
    @Autowired
    AddAuthorityUseCase addUseCase;
    @Autowired
    Permissions permissions;

    @Test
    public void changesAuthority() {
        String username = "EVEGUL";
        var createInput = new AddAuthorityUseCase.InputValues(username, AuthorityLevel.WORLD, ObjectId.get().toHexString(), Authority.OWNER);
        var input = new ChangeAuthorityUseCase.InputValues(username, AuthorityLevel.WORLD, createInput.id(), Authority.PARTICIPANT);

        UserPermissions created = addUseCase.execute(createInput).permissions();

        assertThat(created.hasAuthority(PermissionLevel.world(createInput.id()), createInput.id(), Authority.OWNER)).isTrue();

        UserPermissions changed = useCase.execute(input).permissions();

        assertThat(changed.hasAuthority(PermissionLevel.world(input.id()), input.id(), Authority.PARTICIPANT)).isTrue();
        assertThat(changed.hasAuthority(PermissionLevel.world(input.id()), input.id(), Authority.OWNER)).isFalse();
    }

}
