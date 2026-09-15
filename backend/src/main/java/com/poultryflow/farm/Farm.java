package com.poultryflow.farm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "farms")
class Farm {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "contact_email", length = 254)
    private String contactEmail;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Farm() {
    }

    static Farm create(UUID id, FarmProfileCommand command, Instant now) {
        Farm farm = new Farm();
        farm.id = id;
        farm.name = command.name();
        farm.contactEmail = command.contactEmail();
        farm.contactPhone = command.contactPhone();
        farm.timezone = command.timezone();
        farm.countryCode = command.countryCode();
        farm.currencyCode = command.currencyCode();
        farm.createdAt = now;
        farm.updatedAt = now;
        return farm;
    }

    List<String> update(FarmProfileCommand command, Instant now) {
        List<String> changed = new ArrayList<>();
        name = replace("name", name, command.name(), changed);
        contactEmail = replace("contactEmail", contactEmail, command.contactEmail(), changed);
        contactPhone = replace("contactPhone", contactPhone, command.contactPhone(), changed);
        timezone = replace("timezone", timezone, command.timezone(), changed);
        countryCode = replace("countryCode", countryCode, command.countryCode(), changed);
        currencyCode = replace("currencyCode", currencyCode, command.currencyCode(), changed);
        if (!changed.isEmpty()) {
            updatedAt = now;
        }
        return changed;
    }

    private <T> T replace(String field, T current, T replacement, List<String> changed) {
        if (!Objects.equals(current, replacement)) {
            changed.add(field);
            return replacement;
        }
        return current;
    }

    FarmProfileView toView() {
        return new FarmProfileView(
                id,
                name,
                contactEmail,
                contactPhone,
                timezone,
                countryCode,
                currencyCode,
                version,
                createdAt,
                updatedAt);
    }
}
