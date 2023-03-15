package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.ResourceRepository;
import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@RequiredArgsConstructor
public class GetResourceUseCase extends UseCase<GetResourceUseCase.InputValues, GetResourceUseCase.OutputValues> {

    private final ResourceRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.getResource(input.name));
    }

    public record InputValues(String name) implements UseCase.InputValues { }

    public record OutputValues(Optional<Resource> resource) implements UseCase.OutputValues { }
}