package app.mcorg.permission.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "app.mcorg.permission.infrastructure")
public class DatabaseConfiguration {
}
