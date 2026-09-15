package com.poultryflow.audit;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "audit_events")
class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_subject", nullable = false, updatable = false, length = 255)
    private String actorSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, updatable = false, length = 80)
    private AuditAction action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 80)
    private String entityType;

    @Column(name = "entity_id", nullable = false, updatable = false)
    private UUID entityId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "audit_event_changed_fields",
            joinColumns = @JoinColumn(name = "audit_event_id"))
    @OrderColumn(name = "position")
    @Column(name = "field_name", nullable = false, length = 80)
    private List<String> changedFields = new ArrayList<>();

    protected AuditEvent() {
    }

    AuditEvent(
            UUID id,
            Instant occurredAt,
            String actorSubject,
            AuditAction action,
            String entityType,
            UUID entityId,
            List<String> changedFields) {
        this.id = id;
        this.occurredAt = occurredAt;
        this.actorSubject = actorSubject;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.changedFields = new ArrayList<>(changedFields);
    }
}
