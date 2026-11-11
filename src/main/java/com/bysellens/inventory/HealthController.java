package com.bysellens.inventory;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final RabbitTemplate rabbitTemplate;

    public HealthController(DataSource dataSource, RabbitTemplate rabbitTemplate) {
        this.dataSource = dataSource;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/api/v1/health")
    public ResponseEntity<HealthResponse> health() {
        boolean postgres = postgresAvailable();
        boolean rabbit = rabbitAvailable();
        HealthResponse response = new HealthResponse(
                postgres && rabbit ? "ok" : "unavailable",
                new Dependencies(postgres ? "ok" : "unavailable", rabbit ? "ok" : "unavailable"));
        return ResponseEntity.status(postgres && rabbit ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(response);
    }

    private boolean postgresAvailable() {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean rabbitAvailable() {
        try {
            return Boolean.TRUE.equals(rabbitTemplate.execute(channel -> channel.isOpen()));
        } catch (Exception exception) {
            return false;
        }
    }

    public record HealthResponse(String status, Dependencies dependencies) {
    }

    public record Dependencies(String postgresql, String rabbitmq) {
    }
}
