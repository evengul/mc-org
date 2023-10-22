package app.mcorg.world.domain.usecase.world;

import app.mcorg.common.domain.UnitOfWork;
import app.mcorg.world.domain.model.world.World;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DeleteWorldUseCaseTest {
    @Mock
    private UnitOfWork<World> unit;

    @InjectMocks
    private DeleteWorldUseCase useCase;

    @Test
    public void deletesWorld() {
        String id = ObjectId.get().toHexString();
        useCase.execute(new DeleteWorldUseCase.InputValues(id));
        verify(unit).remove(id);
    }
}
