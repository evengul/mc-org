package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
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
public class ChangeWorldNameUseCaseTest {

    @Mock
    private GetWorldUseCase getWorldUseCase;
    @Mock
    private UnitOfWork<World> unitOfWork;

    @InjectMocks
    private ChangeWorldNameUseCase useCase;

    @Test
    public void changeName() {
        // Given
        World old = World.create("OLD_NAME", new SlimUser("EVEGUL", "EVEGUL"));

        doReturn(new GetWorldUseCase.OutputValues(old))
                .when(getWorldUseCase)
                .execute(any());

        doAnswer(inv -> inv.getArguments()[0])
                .when(unitOfWork)
                .add(any());

        // When
        World newWorld = useCase.execute(new ChangeWorldNameUseCase.InputValues(old.getId(), "NEW_NAME")).world();


        // Then
        assertThat(newWorld.getName()).isEqualTo("NEW_NAME");
    }
}
