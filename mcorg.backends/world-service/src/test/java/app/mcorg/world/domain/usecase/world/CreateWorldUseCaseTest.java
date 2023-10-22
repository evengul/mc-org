package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.common.domain.api.UserProvider;
import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.world.domain.model.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class CreateWorldUseCaseTest {
    @Mock
    private UserProvider userProvider;
    @Mock
    private UnitOfWork<World> unitOfWork;

    @InjectMocks
    private CreateWorldUseCase useCase;


    @Test
    public void createWorld() {
        // Given
        var input = new CreateWorldUseCase.InputValues("NAME");

        doReturn(new SlimUser("EVEGUL", "EVEGUL"))
                .when(userProvider)
                .get();

        doAnswer(inv -> inv.getArguments()[0])
                .when(unitOfWork)
                .add(any());

        // When
        World created = useCase.execute(input).world();

        // Then
        assertThat(created.getName()).isEqualTo("NAME");
        assertThat(created.getUsers()).singleElement().isNotNull();
        assertThat(created.getUsers().get(0).username()).isEqualTo("EVEGUL");
    }
}
