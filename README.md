# MCP Gateway

Реестр MCP-серверов организации, health/probe, прокси tool-calls (MVP). Префикс API: **`/v1/mcp`**, порт **8081**.

## Запуск

```bash
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"  # Windows
./gradlew run
```

## Эндпоинты (скелет)

| Метод | Путь | Описание |
|-------|------|----------|
| GET | `/v1/mcp/` | Информация о сервисе |
| GET | `/v1/mcp/servers` | Список MCP (in-memory; seed `notion`) |
| POST | `/v1/mcp/servers` | Регистрация сервера |
| POST | `/v1/mcp/servers/{id}/health` | Probe (пока `probe_pending`) |

## Notion (spike)

Переменные: `MCP_NOTION_ENABLED`, `NOTION_INTEGRATION_TOKEN`. Полная интеграция — в §7 `cursor-context`.
