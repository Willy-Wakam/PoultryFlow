package com.poultryflow.farm;

import com.poultryflow.audit.AuditAction;
import com.poultryflow.audit.AuditEventAppender;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmProfileService {

    private static final String FARM_ENTITY_TYPE = "FARM";
    private static final List<String> CREATED_FIELDS = List.of(
            "contactEmail",
            "contactPhone",
            "countryCode",
            "currencyCode",
            "name",
            "timezone");

    private final FarmRepository repository;
    private final AuditEventAppender auditEventAppender;

    public FarmProfileService(
            FarmRepository repository,
            AuditEventAppender auditEventAppender) {
        this.repository = repository;
        this.auditEventAppender = auditEventAppender;
    }

    @Transactional(readOnly = true)
    public FarmProfileView getCurrent() {
        return resolveCurrent().toView();
    }

    @Transactional
    public FarmProfileView saveCurrent(FarmProfileCommand rawCommand, String actorSubject) {
        FarmProfileCommand command = canonicalize(rawCommand);
        List<Farm> current = repository.findTop2ByOrderByCreatedAtAscIdAsc();
        if (current.size() > 1) {
            throw new AmbiguousFarmProfileException();
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (current.isEmpty()) {
            Farm farm = repository.saveAndFlush(Farm.create(UUID.randomUUID(), command, now));
            auditEventAppender.append(
                    actorSubject,
                    AuditAction.FARM_PROFILE_CREATED,
                    FARM_ENTITY_TYPE,
                    farm.toView().id(),
                    CREATED_FIELDS);
            return farm.toView();
        }

        Farm farm = current.getFirst();
        List<String> changedFields = farm.update(command, now);
        if (changedFields.isEmpty()) {
            return farm.toView();
        }

        repository.flush();
        auditEventAppender.append(
                actorSubject,
                AuditAction.FARM_PROFILE_UPDATED,
                FARM_ENTITY_TYPE,
                farm.toView().id(),
                changedFields);
        return farm.toView();
    }

    private Farm resolveCurrent() {
        List<Farm> current = repository.findTop2ByOrderByCreatedAtAscIdAsc();
        if (current.isEmpty()) {
            throw new FarmProfileNotConfiguredException();
        }
        if (current.size() > 1) {
            throw new AmbiguousFarmProfileException();
        }
        return current.getFirst();
    }

    private FarmProfileCommand canonicalize(FarmProfileCommand command) {
        return new FarmProfileCommand(
                command.name().trim(),
                nullableTrim(command.contactEmail()),
                nullableTrim(command.contactPhone()),
                command.timezone().trim(),
                defaultedUppercase(command.countryCode(), "CM"),
                defaultedUppercase(command.currencyCode(), "XAF"));
    }

    private String nullableTrim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String defaultedUppercase(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
