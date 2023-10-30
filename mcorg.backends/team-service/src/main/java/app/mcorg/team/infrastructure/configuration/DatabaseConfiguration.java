package app.mcorg.team.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "app.mcorg.team.infrastructure")
public class DatabaseConfiguration {
}
