package com.poultryflow.identity.membership.api;

import jakarta.validation.constraints.NotNull;

public record UpdateFarmMembershipStatusRequest(
        @NotNull(message = "is required") Boolean enabled) {
}
