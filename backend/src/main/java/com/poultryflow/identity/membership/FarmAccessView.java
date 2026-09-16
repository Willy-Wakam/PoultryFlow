package com.poultryflow.identity.membership;

import com.poultryflow.identity.access.PoultryFlowRole;
import java.util.List;
import java.util.UUID;

public record FarmAccessView(
        UUID farmId,
        UUID membershipId,
        List<PoultryFlowRole> roles,
        FarmMembershipStatus status,
        boolean bootstrapAuthority) {
}
