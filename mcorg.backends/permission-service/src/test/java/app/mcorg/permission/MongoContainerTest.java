package app.mcorg.permission;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.RabbitMQContainer;

public class MongoContainerTest {
    static MongoDBContainer dbContainer = new MongoDBContainer("mongo:7.0.2");

    static RabbitMQContainer rabbitMQContainer = new RabbitMQContainer("rabbitmq:3-management-alpine")
            .withExposedPorts(5672, 15672)
            .withUser("guest", "guest");

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        dbContainer.start();
        rabbitMQContainer.start();
        registry.add("spring.data.mongodb.uri", dbContainer::getConnectionString);
        registry.add("spring.rabbitmq.host", rabbitMQContainer::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQContainer::getAmqpPort);
        registry.add("spring.rabbitmq.username", () -> "guest");
        registry.add("spring.rabbitmq.password", () -> "guest");
    }
}
