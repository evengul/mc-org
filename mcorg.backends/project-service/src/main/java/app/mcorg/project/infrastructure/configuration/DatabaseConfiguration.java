package app.mcorg.project.infrastructure.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(basePackages = "app.mcorg.project.infrastructure")
public class DatabaseConfiguration { }
