CREATE TABLE farm_memberships (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms (id),
    keycloak_subject VARCHAR(255),
    normalized_email VARCHAR(254) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT farm_memberships_farm_email_unique UNIQUE (farm_id, normalized_email),
    CONSTRAINT farm_memberships_email_normalized
        CHECK (normalized_email = LOWER(BTRIM(normalized_email)) AND BTRIM(normalized_email) <> ''),
    CONSTRAINT farm_memberships_status_valid
        CHECK (status IN ('INVITED', 'ACTIVE', 'DISABLED')),
    CONSTRAINT farm_memberships_active_has_subject
        CHECK (status <> 'ACTIVE' OR keycloak_subject IS NOT NULL),
    CONSTRAINT farm_memberships_invited_has_no_subject
        CHECK (status <> 'INVITED' OR keycloak_subject IS NULL)
);

CREATE UNIQUE INDEX farm_memberships_farm_subject_unique
    ON farm_memberships (farm_id, keycloak_subject)
    WHERE keycloak_subject IS NOT NULL;

CREATE INDEX farm_memberships_farm_status_idx
    ON farm_memberships (farm_id, status);

CREATE TABLE farm_membership_roles (
    membership_id UUID NOT NULL REFERENCES farm_memberships (id),
    role VARCHAR(24) NOT NULL,
    PRIMARY KEY (membership_id, role),
    CONSTRAINT farm_membership_roles_value_valid
        CHECK (role IN ('OWNER', 'MANAGER', 'STAFF', 'ACCOUNTANT', 'VIEWER'))
);
