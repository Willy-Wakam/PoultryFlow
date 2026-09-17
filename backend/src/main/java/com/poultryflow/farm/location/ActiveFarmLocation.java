package com.poultryflow.farm.location;

import java.util.UUID;

public record ActiveFarmLocation(
        UUID id,
        UUID farmId,
        FarmLocationType type) {
}
