package app.mcorg.server.presentation.instant;

import app.mcorg.server.core.usecase.server.StartServerUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("local")
@Component
@RequiredArgsConstructor
public class LocalServer implements ApplicationRunner {
    private final StartServerUseCase startServerUseCase;

    @Override
    public void run(ApplicationArguments args) {
        startServerUseCase.execute(new StartServerUseCase.InputValues("1.18.2"));
    }
}
