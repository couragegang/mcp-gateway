ALTER TABLE mcp_catalog_tools
    ADD COLUMN IF NOT EXISTS runtime_base_url TEXT,
    ADD COLUMN IF NOT EXISTS runtime_kind TEXT NOT NULL DEFAULT 'http';

UPDATE mcp_catalog_tools
SET runtime_base_url = 'http://mcp-notion:8091/v1/notion',
    runtime_kind = 'http'
WHERE connector_key = 'notion';
