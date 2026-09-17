package com.poultryflow.farm.location.api;

import com.poultryflow.farm.location.FarmLocationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateFarmLocationStatusRequest(
        @NotNull(message = "is required")
        FarmLocationStatus status,

        @NotNull(message = "is required")
        @PositiveOrZero(message = "must be zero or greater")
        Long version) {
}
