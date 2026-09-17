CREATE TABLE farm_locations (
    id UUID PRIMARY KEY,
    farm_id UUID NOT NULL REFERENCES farms (id),
    name VARCHAR(120) NOT NULL,
    normalized_name VARCHAR(120) NOT NULL,
    type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    version BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT farm_locations_farm_name_unique UNIQUE (farm_id, normalized_name),
    CONSTRAINT farm_locations_name_trimmed
        CHECK (name = BTRIM(name) AND name <> ''),
    CONSTRAINT farm_locations_name_normalized
        CHECK (normalized_name = LOWER(name)),
    CONSTRAINT farm_locations_type_valid
        CHECK (type IN ('HOUSE', 'PEN', 'STORAGE', 'OTHER')),
    CONSTRAINT farm_locations_status_valid
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX farm_locations_farm_status_name_idx
    ON farm_locations (farm_id, status, normalized_name, id);
