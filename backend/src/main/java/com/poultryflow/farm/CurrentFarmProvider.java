package com.poultryflow.farm;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Farm-owned boundary for resolving and serializing work against the current farm. */
@Component
public class CurrentFarmProvider {

    private final FarmRepository repository;

    public CurrentFarmProvider(FarmRepository repository) {
        this.repository = repository;
    }

    public Optional<UUID> findCurrentFarmId() {
        List<Farm> current = repository.findTop2ByOrderByCreatedAtAscIdAsc();
        if (current.size() > 1) {
            throw new AmbiguousFarmProfileException();
        }
        return current.stream().findFirst().map(farm -> farm.toView().id());
    }

    public UUID requireCurrentFarmId() {
        return findCurrentFarmId().orElseThrow(FarmProfileNotConfiguredException::new);
    }

    public UUID lockCurrentFarmId() {
        UUID farmId = requireCurrentFarmId();
        return repository.findByIdForUpdate(farmId)
                .orElseThrow(FarmProfileNotConfiguredException::new)
                .toView()
                .id();
    }
}
