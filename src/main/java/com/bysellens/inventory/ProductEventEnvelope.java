package com.bysellens.inventory;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ProductEventEnvelope(
        UUID eventId,
        String eventType,
        UUID aggregateId,
        String aggregateType,
        Instant occurredAt,
        int version,
        Map<String, Object> payload,
        Metadata metadata) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Metadata(UUID correlationId, UUID causationId, UUID userId) {
    }
}
