package com.poultryflow.farm.location;

import java.util.UUID;

/** Farm-owned boundary for validating locations used by future business records. */
public interface ActiveFarmLocationProvider {

    ActiveFarmLocation requireActive(UUID farmId, UUID locationId);
}
