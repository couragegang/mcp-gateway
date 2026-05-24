UPDATE mcp_catalog_tools
SET connection_form_schema = '{
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
      "label": {"ru": "База или страница для записей"},
      "placeholder": {"ru": "Выберите из списка ниже или вставьте ссылку Copy link"},
      "required": false,
      "sensitive": false,
      "widget": "text",
      "storage": "config"
    }
  ]
}'::jsonb
WHERE connector_key = 'notion';
