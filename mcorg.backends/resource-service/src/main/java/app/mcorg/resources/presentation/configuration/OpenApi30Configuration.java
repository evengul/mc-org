package app.mcorg.resources.presentation.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MCORG Resource API",
                version = "v1"
        )
)
public class OpenApi30Configuration {
}
