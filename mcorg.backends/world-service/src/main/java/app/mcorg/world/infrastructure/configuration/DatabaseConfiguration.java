package app.mcorg.world.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "app.mcorg.world.infrastructure")
public class DatabaseConfiguration {
}
