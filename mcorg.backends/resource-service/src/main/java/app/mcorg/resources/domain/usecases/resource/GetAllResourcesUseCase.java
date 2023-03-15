package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.ResourceRepository;
import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetAllResourcesUseCase extends UseCase<GetAllResourcesUseCase.InputValues, GetAllResourcesUseCase.OutputValues> {

    private final ResourceRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(repository.getResources());
    }

    public record InputValues() implements UseCase.InputValues { }

    public record OutputValues(List<Resource> resources) implements UseCase.OutputValues { }
}