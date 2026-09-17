package com.poultryflow.identity.membership;

import com.poultryflow.identity.access.PoultryFlowRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FarmMembershipView(
        UUID id,
        UUID farmId,
        String email,
        List<PoultryFlowRole> roles,
        FarmMembershipStatus status,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
