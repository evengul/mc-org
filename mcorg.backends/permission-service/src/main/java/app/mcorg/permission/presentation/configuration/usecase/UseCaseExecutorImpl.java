package app.mcorg.permission.presentation.configuration.usecase;

import app.mcorg.permission.domain.usecase.UseCase;
import app.mcorg.permission.domain.usecase.UseCaseExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
public class UseCaseExecutorImpl implements UseCaseExecutor {
    private final ThreadPoolTaskExecutor executor;

    @Override
    public <RX, I extends UseCase.InputValues, O extends UseCase.OutputValues> CompletableFuture<RX> execute(UseCase<I, O> useCase, I input, Function<O, RX> outputMapper) {
        return CompletableFuture
                .supplyAsync(() -> input, executor)
                .thenApplyAsync(useCase::execute, executor)
                .thenApplyAsync(outputMapper, executor);
    }
}
