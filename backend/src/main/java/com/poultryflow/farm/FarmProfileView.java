package com.poultryflow.farm;

import java.time.Instant;
import java.util.UUID;

public record FarmProfileView(
        UUID id,
        String name,
        String contactEmail,
        String contactPhone,
        String timezone,
        String countryCode,
        String currencyCode,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
