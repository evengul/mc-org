package app.mcorg.server;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@RequiredArgsConstructor
public class ServerService {

    public static void main(String[] args) {
        SpringApplication.run(ServerService.class, args);
    }

}
