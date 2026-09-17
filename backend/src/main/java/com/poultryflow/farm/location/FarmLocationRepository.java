package com.poultryflow.farm.location;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface FarmLocationRepository extends JpaRepository<FarmLocation, UUID> {

    Optional<FarmLocation> findByIdAndFarmId(UUID id, UUID farmId);

    boolean existsByFarmIdAndNormalizedName(UUID farmId, String normalizedName);

    boolean existsByFarmIdAndNormalizedNameAndIdNot(
            UUID farmId,
            String normalizedName,
            UUID id);

    List<FarmLocation> findAllByFarmIdOrderByNormalizedNameAscIdAsc(UUID farmId);

    List<FarmLocation> findAllByFarmIdAndStatusOrderByNormalizedNameAscIdAsc(
            UUID farmId,
            FarmLocationStatus status);
}
