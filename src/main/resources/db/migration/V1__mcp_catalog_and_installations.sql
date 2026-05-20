CREATE TABLE mcp_catalog_tools (
    connector_key         TEXT PRIMARY KEY,
    display_name          TEXT NOT NULL,
    description           TEXT,
    transport             TEXT NOT NULL DEFAULT 'http_sse',
    endpoint_template     TEXT,
    connection_form_schema JSONB NOT NULL,
    form_schema_version   INT NOT NULL DEFAULT 1,
    policy_template_pack  JSONB,
    policy_pack_version   INT NOT NULL DEFAULT 1,
    status                TEXT NOT NULL DEFAULT 'published'
        CHECK (status IN ('published', 'deprecated'))
);

CREATE TABLE workspace_mcp_installations (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL,
    workspace_id          UUID NOT NULL,
    connector_key         TEXT NOT NULL REFERENCES mcp_catalog_tools (connector_key),
    display_label         TEXT,
    credential_secret_ref TEXT,
    connection_config     JSONB NOT NULL DEFAULT '{}',
    status                TEXT NOT NULL DEFAULT 'active'
        CHECK (status IN ('active', 'error', 'revoked')),
    installed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    installed_by_user_id  UUID,
    CONSTRAINT workspace_mcp_installations_ws_connector_uk UNIQUE (workspace_id, connector_key)
);

CREATE INDEX workspace_mcp_installations_workspace_idx ON workspace_mcp_installations (workspace_id);

CREATE TABLE mcp_health_checks (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    installation_id  UUID NOT NULL REFERENCES workspace_mcp_installations (id) ON DELETE CASCADE,
    checked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    result           TEXT NOT NULL,
    details          JSONB
);
