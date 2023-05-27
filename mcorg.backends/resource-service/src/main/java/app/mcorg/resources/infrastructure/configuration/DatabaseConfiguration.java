package app.mcorg.resources.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "app.mcorg.resources.infrastructure")
public class DatabaseConfiguration {
}
