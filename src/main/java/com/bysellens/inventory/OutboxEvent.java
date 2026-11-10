package com.bysellens.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "routing_key", nullable = false, length = 128)
    private String routingKey;

    @Column(name = "event_payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String eventPayload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
    }

    public OutboxEvent(UUID eventId, String eventType, String routingKey, String eventPayload) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.eventPayload = eventPayload;
        this.createdAt = Instant.now();
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public String getEventPayload() {
        return eventPayload;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished() {
        publishedAt = Instant.now();
    }

    public void recordAttempt() {
        attempts++;
    }
}
