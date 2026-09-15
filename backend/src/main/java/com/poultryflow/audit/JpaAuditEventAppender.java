package com.poultryflow.audit;

import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class JpaAuditEventAppender implements AuditEventAppender {

    private final AuditEventRepository repository;

    JpaAuditEventAppender(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void append(
            String actorSubject,
            AuditAction action,
            String entityType,
            UUID entityId,
            Collection<String> changedFields) {
        var deterministicFields = changedFields.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        repository.save(new AuditEvent(
                UUID.randomUUID(),
                Instant.now(),
                actorSubject,
                action,
                entityType,
                entityId,
                deterministicFields));
    }
}
