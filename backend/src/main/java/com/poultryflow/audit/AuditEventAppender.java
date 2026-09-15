package com.poultryflow.audit;

import java.util.Collection;
import java.util.UUID;

/** Boundary for recording immutable business audit events in the current transaction. */
public interface AuditEventAppender {

    void append(
            String actorSubject,
            AuditAction action,
            String entityType,
            UUID entityId,
            Collection<String> changedFields);
}
