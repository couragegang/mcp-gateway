# mcp-gateway

Платформенный **каталог MCP** и **установки в workspace** (`/v1/mcp`).

Контракт: [`../api-contracts/mcp/openapi.yaml`](../api-contracts/mcp/openapi.yaml)

## Запуск

```bash
docker compose up --build
```

- API: http://localhost:8081/v1/mcp/
- Postgres: localhost:5435

## API (MVP)

| Метод | Путь |
|-------|------|
| GET | `/catalog` |
| GET | `/catalog/{connectorKey}` |
| GET | `/workspaces/{workspaceId}/installations` |
| POST | `/workspaces/{workspaceId}/installations` — заголовки `X-Org-Id`, опционально `X-User-Id` |
| GET | `/workspaces/{workspaceId}/installations/{id}` — детали (форма, policy pack) |
| PATCH | `/workspaces/{workspaceId}/installations/{id}` — label, credentials, policy |
| POST | `/workspaces/{workspaceId}/installations/{id}/health` |
| DELETE | `/workspaces/{workspaceId}/installations/{id}` |

Notion seed в Flyway `V2__seed_notion_catalog.sql`. Policy apply — заглушка (лог).
