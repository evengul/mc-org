package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.ResourceRepository;
import app.mcorg.resources.domain.model.exceptions.Exceptions;
import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class GetOrThrowResourceUseCase extends UseCase<GetOrThrowResourceUseCase.InputValues, GetOrThrowResourceUseCase.OutputValues> {

    private final ResourceRepository repository;

    public OutputValues execute(InputValues input) {
        final String name = input.name;
        Resource resource = repository.getResource(name)
                .orElseThrow(Exceptions.notFound(name));
        return new OutputValues(resource);
    }

    public record InputValues(String name) implements UseCase.InputValues {
    }

    public record OutputValues(Resource resource) implements UseCase.OutputValues {
    }
}