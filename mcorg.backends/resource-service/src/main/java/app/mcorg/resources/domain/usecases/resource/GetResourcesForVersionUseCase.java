package app.mcorg.resources.domain.usecases.resource;

import app.mcorg.resources.domain.api.ResourceRepository;
import app.mcorg.resources.domain.model.resource.Resource;
import app.mcorg.resources.domain.usecases.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class GetResourcesForVersionUseCase extends UseCase<GetResourcesForVersionUseCase.InputValues, GetResourcesForVersionUseCase.OutputValues> {

    private final ResourceRepository repository;

    public OutputValues execute(InputValues input) {
        return new OutputValues(input.version, repository.getResources(input.version));
    }

    public record InputValues(String version) implements UseCase.InputValues { }

    public record OutputValues(String version, List<Resource> resources) implements UseCase.OutputValues { }
}