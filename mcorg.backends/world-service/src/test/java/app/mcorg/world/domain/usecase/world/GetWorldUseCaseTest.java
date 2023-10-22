package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.model.SlimUser;
import app.mcorg.world.domain.api.Worlds;
import app.mcorg.world.domain.model.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
public class GetWorldUseCaseTest {
    @Mock
    private Worlds worlds;

    @InjectMocks
    private GetWorldUseCase useCase;

    @Test
    public void getsWorld() {
        World world = World.create("NAME", new SlimUser("EVEGUL", "EVEGUL"));

        doReturn(Optional.of(world))
                .when(worlds)
                .get(any());

        World retrieved = useCase.execute(new GetWorldUseCase.InputValues(world.getId())).world();

        assertThat(retrieved).isEqualTo(world);
    }
}
