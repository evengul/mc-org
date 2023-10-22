package app.mcorg.world;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;

public class MongoContainerTest {
    @Container
    static MongoDBContainer dbContainer = new MongoDBContainer("mongo:7.0.1");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        dbContainer.start();
        registry.add("spring.data.mongodb.uri", dbContainer::getConnectionString);
    }
}
