INSERT INTO mcp_catalog_tools (
    connector_key,
    display_name,
    description,
    transport,
    connection_form_schema,
    form_schema_version,
    policy_template_pack,
    policy_pack_version,
    status
) VALUES (
    'notion',
    'Notion',
    'Поиск и управление страницами Notion через MCP',
    'http_sse',
    '{
      "schema_version": 1,
      "title": {"ru": "Подключение Notion"},
      "fields": [
        {
          "key": "integration_token",
          "type": "string",
          "label": {"ru": "Integration token"},
          "required": true,
          "sensitive": true,
          "widget": "password",
          "storage": "secret"
        },
        {
          "key": "default_database_id",
          "type": "string",
          "label": {"ru": "ID базы по умолчанию"},
          "required": false,
          "sensitive": false,
          "widget": "text",
          "storage": "config"
        }
      ]
    }'::jsonb,
    1,
    '{
      "rules": [
        {"effect": "allow_read", "resource_pattern": "mcp:notion:*:read", "priority": 100},
        {"effect": "require_approval", "resource_pattern": "mcp:notion:*:write", "priority": 200}
      ]
    }'::jsonb,
    1,
    'published'
) ON CONFLICT (connector_key) DO NOTHING;
