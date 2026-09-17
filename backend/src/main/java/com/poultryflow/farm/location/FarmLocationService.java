package com.poultryflow.farm.location;

import com.poultryflow.audit.AuditAction;
import com.poultryflow.audit.AuditEventAppender;
import com.poultryflow.farm.CurrentFarmProvider;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmLocationService implements ActiveFarmLocationProvider {

    private static final String LOCATION_ENTITY_TYPE = "FARM_LOCATION";
    private static final List<String> CREATED_FIELDS = List.of("name", "status", "type");
    private static final List<String> STATUS_FIELDS = List.of("status");

    private final FarmLocationRepository repository;
    private final CurrentFarmProvider currentFarmProvider;
    private final AuditEventAppender auditEventAppender;

    public FarmLocationService(
            FarmLocationRepository repository,
            CurrentFarmProvider currentFarmProvider,
            AuditEventAppender auditEventAppender) {
        this.repository = repository;
        this.currentFarmProvider = currentFarmProvider;
        this.auditEventAppender = auditEventAppender;
    }

    @Transactional(readOnly = true)
    public List<FarmLocationView> listCurrent(boolean includeInactive) {
        UUID farmId = currentFarmProvider.requireCurrentFarmId();
        List<FarmLocation> locations = includeInactive
                ? repository.findAllByFarmIdOrderByNormalizedNameAscIdAsc(farmId)
                : repository.findAllByFarmIdAndStatusOrderByNormalizedNameAscIdAsc(
                        farmId, FarmLocationStatus.ACTIVE);
        return locations.stream().map(FarmLocation::toView).toList();
    }

    @Transactional
    public FarmLocationView createCurrent(FarmLocationCommand rawCommand, String actorSubject) {
        UUID farmId = currentFarmProvider.requireCurrentFarmId();
        CanonicalLocation command = canonicalize(rawCommand);
        if (repository.existsByFarmIdAndNormalizedName(farmId, command.normalizedName())) {
            throw new DuplicateFarmLocationNameException();
        }

        try {
            FarmLocation location = repository.saveAndFlush(FarmLocation.create(
                    UUID.randomUUID(),
                    farmId,
                    command.name(),
                    command.normalizedName(),
                    command.type(),
                    now()));
            auditEventAppender.append(
                    actorSubject,
                    AuditAction.FARM_LOCATION_CREATED,
                    LOCATION_ENTITY_TYPE,
                    location.id(),
                    CREATED_FIELDS);
            return location.toView();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateFarmLocationNameException();
        }
    }

    @Transactional
    public FarmLocationView updateCurrent(
            UUID locationId,
            long expectedVersion,
            FarmLocationCommand rawCommand,
            String actorSubject) {
        UUID farmId = currentFarmProvider.requireCurrentFarmId();
        FarmLocation location = location(farmId, locationId);
        requireVersion(location, expectedVersion);
        CanonicalLocation command = canonicalize(rawCommand);
        if (!location.normalizedName().equals(command.normalizedName())
                && repository.existsByFarmIdAndNormalizedNameAndIdNot(
                        farmId, command.normalizedName(), locationId)) {
            throw new DuplicateFarmLocationNameException();
        }

        List<String> changed = location.update(
                command.name(), command.normalizedName(), command.type(), now());
        if (changed.isEmpty()) {
            return location.toView();
        }

        try {
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateFarmLocationNameException();
        }
        auditEventAppender.append(
                actorSubject,
                AuditAction.FARM_LOCATION_UPDATED,
                LOCATION_ENTITY_TYPE,
                location.id(),
                changed);
        return location.toView();
    }

    @Transactional
    public FarmLocationView changeCurrentStatus(
            UUID locationId,
            long expectedVersion,
            FarmLocationStatus status,
            String actorSubject) {
        UUID farmId = currentFarmProvider.requireCurrentFarmId();
        FarmLocation location = location(farmId, locationId);
        requireVersion(location, expectedVersion);
        if (!location.changeStatus(status, now())) {
            return location.toView();
        }

        repository.flush();
        auditEventAppender.append(
                actorSubject,
                status == FarmLocationStatus.ACTIVE
                        ? AuditAction.FARM_LOCATION_ACTIVATED
                        : AuditAction.FARM_LOCATION_DEACTIVATED,
                LOCATION_ENTITY_TYPE,
                location.id(),
                STATUS_FIELDS);
        return location.toView();
    }

    @Override
    @Transactional(readOnly = true)
    public ActiveFarmLocation requireActive(UUID farmId, UUID locationId) {
        FarmLocation location = repository.findByIdAndFarmId(locationId, farmId)
                .orElseThrow(ActiveFarmLocationRequiredException::new);
        if (!location.isActive()) {
            throw new ActiveFarmLocationRequiredException();
        }
        return location.toActiveReference();
    }

    private FarmLocation location(UUID farmId, UUID locationId) {
        return repository.findByIdAndFarmId(locationId, farmId)
                .orElseThrow(FarmLocationNotFoundException::new);
    }

    private void requireVersion(FarmLocation location, long expectedVersion) {
        if (location.version() != expectedVersion) {
            throw new ConcurrentFarmLocationModificationException();
        }
    }

    private CanonicalLocation canonicalize(FarmLocationCommand command) {
        String name = command.name().trim();
        return new CanonicalLocation(name, name.toLowerCase(Locale.ROOT), command.type());
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private record CanonicalLocation(
            String name,
            String normalizedName,
            FarmLocationType type) {
    }
}
