package com.bysellens.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inventory.events.publisher-enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outbox;
    private final RabbitTemplate rabbit;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final Duration confirmTimeout;

    public OutboxPublisher(
            OutboxEventRepository outbox,
            RabbitTemplate rabbit,
            ObjectMapper objectMapper,
            @Value("${inventory.events.exchange}") String exchange,
            @Value("${inventory.events.confirm-timeout-ms:5000}") long confirmTimeoutMillis) {
        if (exchange.isBlank()) {
            throw new IllegalArgumentException("inventory.events.exchange is required when the publisher is enabled.");
        }
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.confirmTimeout = Duration.ofMillis(confirmTimeoutMillis);
    }

    @Scheduled(fixedDelayString = "${inventory.events.poll-interval-ms:5000}")
    public void publishPending() {
        for (OutboxEvent event : outbox.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                ProductEventEnvelope envelope = objectMapper.readValue(event.getEventPayload(),
                        ProductEventEnvelope.class);
                CorrelationData confirmation = new CorrelationData(event.getEventId().toString());
                rabbit.convertAndSend(exchange, event.getRoutingKey(), envelope, message -> {
                    message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    message.getMessageProperties().setMessageId(event.getEventId().toString());
                    return message;
                }, confirmation);
                CorrelationData.Confirm result = confirmation.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!result.isAck() || confirmation.getReturned() != null) {
                    throw new IllegalStateException("Broker did not confirm event delivery.");
                }
                event.markPublished();
                outbox.save(event);
            } catch (Exception exception) {
                event.recordAttempt();
                outbox.save(event);
                log.warn("Could not publish inventory event {}; it remains pending for retry.", event.getEventId());
            }
        }
    }
}
