CREATE TABLE farms (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(254),
    contact_phone VARCHAR(40),
    timezone VARCHAR(64) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT farms_name_not_blank CHECK (BTRIM(name) <> ''),
    CONSTRAINT farms_country_code_format CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT farms_currency_code_format CHECK (currency_code ~ '^[A-Z]{3}$')
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_subject VARCHAR(255) NOT NULL,
    action_type VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID NOT NULL,
    CONSTRAINT audit_events_actor_not_blank CHECK (BTRIM(actor_subject) <> ''),
    CONSTRAINT audit_events_action_not_blank CHECK (BTRIM(action_type) <> ''),
    CONSTRAINT audit_events_entity_type_not_blank CHECK (BTRIM(entity_type) <> '')
);

CREATE INDEX audit_events_entity_idx
    ON audit_events (entity_type, entity_id, occurred_at);

CREATE TABLE audit_event_changed_fields (
    audit_event_id UUID NOT NULL REFERENCES audit_events (id),
    position INTEGER NOT NULL,
    field_name VARCHAR(80) NOT NULL,
    PRIMARY KEY (audit_event_id, position),
    CONSTRAINT audit_event_changed_fields_unique UNIQUE (audit_event_id, field_name),
    CONSTRAINT audit_event_changed_fields_name_not_blank CHECK (BTRIM(field_name) <> '')
);

CREATE FUNCTION reject_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit records are append-only';
END;
$$;

CREATE TRIGGER audit_events_append_only
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

CREATE TRIGGER audit_event_changed_fields_append_only
    BEFORE UPDATE OR DELETE ON audit_event_changed_fields
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
