package com.poultryflow.farm.location;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "farm_locations")
class FarmLocation {

    @Id
    private UUID id;

    @Column(name = "farm_id", nullable = false, updatable = false)
    private UUID farmId;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 120)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FarmLocationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private FarmLocationStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected FarmLocation() {
    }

    static FarmLocation create(
            UUID id,
            UUID farmId,
            String name,
            String normalizedName,
            FarmLocationType type,
            Instant now) {
        FarmLocation location = new FarmLocation();
        location.id = id;
        location.farmId = farmId;
        location.name = name;
        location.normalizedName = normalizedName;
        location.type = type;
        location.status = FarmLocationStatus.ACTIVE;
        location.createdAt = now;
        location.updatedAt = now;
        return location;
    }

    List<String> update(
            String replacementName,
            String replacementNormalizedName,
            FarmLocationType replacementType,
            Instant now) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(name, replacementName)) {
            name = replacementName;
            normalizedName = replacementNormalizedName;
            changed.add("name");
        }
        if (type != replacementType) {
            type = replacementType;
            changed.add("type");
        }
        if (!changed.isEmpty()) {
            updatedAt = now;
        }
        return changed;
    }

    boolean changeStatus(FarmLocationStatus replacement, Instant now) {
        if (status == replacement) {
            return false;
        }
        status = replacement;
        updatedAt = now;
        return true;
    }

    UUID id() {
        return id;
    }

    UUID farmId() {
        return farmId;
    }

    String normalizedName() {
        return normalizedName;
    }

    long version() {
        return version;
    }

    boolean isActive() {
        return status == FarmLocationStatus.ACTIVE;
    }

    FarmLocationView toView() {
        return new FarmLocationView(
                id,
                farmId,
                name,
                type,
                status,
                version,
                createdAt,
                updatedAt);
    }

    ActiveFarmLocation toActiveReference() {
        return new ActiveFarmLocation(id, farmId, type);
    }
}
