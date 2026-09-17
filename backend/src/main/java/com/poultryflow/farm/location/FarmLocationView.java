package com.poultryflow.farm.location;

import java.time.Instant;
import java.util.UUID;

public record FarmLocationView(
        UUID id,
        UUID farmId,
        String name,
        FarmLocationType type,
        FarmLocationStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
