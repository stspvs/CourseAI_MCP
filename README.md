# Валютный дозор (Currency Watch MCP)

Локальный MCP-сервер на Kotlin Multiplatform: периодическая загрузка курсов ЦБ РФ в SQLite (SQLDelight), инструмент `get_currency_summary` для агрегатов за окно по часам.

## Сборка и запуск (JVM)

Требуется JDK 21 (или настройте `kotlin { jvmToolchain(...) }` в [mcp-server/build.gradle.kts](mcp-server/build.gradle.kts)).

```bat
gradlew.bat :mcp-server:run
```

Интервал фонового обновления курсов с ЦБ (по умолчанию раз в час):

```bat
gradlew.bat :mcp-server:run --args="--sync-interval=PT30M"
```

Формат — **ISO-8601 duration**: `PT1H` (1 час), `PT30M` (30 минут), `PT15M` и т.д. Справка: `gradlew.bat :mcp-server:run --args=--help`

Рабочая директория — корень репозитория. Данные и логи: `%USERPROFILE%\.currency-watch\` — там же лежит SQLite (`currency.db`) и **файлы логов** `mcp-server.log` (ротация по дням: `mcp-server.yyyy-MM-dd.log`). Дублирование в **stderr** (не в stdout — там протокол MCP).

## Подключение клиента (stdio)

Сервер использует stdin/stdout; в консоль **stdout** нельзя писать произвольный текст (ломается протокол MCP). Логи при необходимости — в stderr.

### Claude Desktop (Windows)

В конфигурации MCP добавьте сервер, например:

```json
{
  "mcpServers": {
    "currency-watch": {
      "command": "C:\\ПУТЬ\\К\\gradlew.bat",
      "args": [":mcp-server:run", "--args=--sync-interval=PT1H"],
      "cwd": "C:\\Users\\sts-dev\\AndroidStudioProjects\\ai_projects\\CourseAI_MCP"
    }
  }
}
```

Укажите полный путь к `gradlew.bat` и к каталогу проекта на вашей машине.

### Cursor / другие клиенты

Аналогично: команда — `gradlew.bat`, аргументы например `:mcp-server:run` и при необходимости `--args=--sync-interval=PT30M`, `cwd` — корень проекта.

## Инструмент `get_currency_summary`

- `hours` (integer, опционально): окно в часах, по умолчанию 24.
- `currencies` (массив строк, опционально): коды валют, например `["USD","EUR"]`. Если не задан — все валюты в окне.

Ответ содержит текстовую сводку и JSON с полями `avg`, `min`, `max`, `change`, `sampleCount` по каждой валюте.

## Модули

- `shared` — общая логика (API ЦБ, парсинг XML, репозиторий, агрегатор, SQLDelight, `expect`/`actual` для HTTP и планировщика). Реальная БД и MCP — на JVM; Android/iOS — заглушки планировщика.
- `mcp-server` — точка входа MCP (stdio, официальный `kotlin-sdk-server`).

## Планировщик

На JVM фоновая синхронизация с ЦБ запускается раз в час; при старте выполняется одна немедленная попытка загрузки.
