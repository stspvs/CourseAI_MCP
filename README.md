# Валютный дозор (Currency Watch MCP)

Локальный MCP-сервер на Kotlin Multiplatform: периодическая загрузка курсов ЦБ РФ в SQLite (SQLDelight), инструмент `get_currency_summary` для агрегатов за окно по часам.

## Сборка и запуск (JVM)

Требуется JDK 21 (или настройте `kotlin { jvmToolchain(...) }` в [mcp-server/build.gradle.kts](mcp-server/build.gradle.kts)).

В репозитории **нет** модуля `composeApp` — это не отдельное Compose-приложение. Запускаемый процесс — **JVM-приложение** в модуле **`mcp-server`**.

Из корня проекта:

```bat
gradlew.bat runMcp
```

Полный эквивалент (удобно для передачи `--args`):

```bat
gradlew.bat :mcp-server:run
```

**Режим stdio для клиентов (Cursor, Claude и т.д.):** Gradle по умолчанию пишет служебные строки в консоль и может **испортить stdout**, по которому идёт протокол MCP — клиент тогда сразу показывает **connection closed**. Запускайте с **тихим** Gradle и без лишнего вывода:

```bat
gradlew.bat -q --no-daemon :mcp-server:run
```

Или готовый скрипт из репозитория (он делает то же самое):

```bat
scripts\run-mcp-stdio.bat
```

Альтернатива без Gradle: один раз собрать дистрибутив и вызывать только `java` (в stdout не попадает вывод Gradle):

```bat
gradlew.bat :mcp-server:installDist
```

Затем в конфиге MCP указать **`mcp-server\build\install\mcp-server\bin\mcp-server.bat`** (полный путь), `cwd` можно не задавать. Подробности — в разделе «Если connection closed» ниже.

В **Android Studio / IntelliJ**: Run → Edit Configurations → **Gradle** → задача **`mcp-server:run`** (не `composeApp` и не `:shared:assemble`).

Параметры JVM (`--args=...`) надёжнее передавать через **`gradlew :mcp-server:run --args="..."`**, а не через `runMcp`.

Интервал фонового обновления курсов с ЦБ (по умолчанию раз в час):

```bat
gradlew.bat :mcp-server:run --args="--sync-interval=PT30M"
```

Формат — **ISO-8601 duration**: `PT1H` (1 час), `PT30M` (30 минут), `PT15M` и т.д. Справка: `gradlew.bat :mcp-server:run --args=--help`

### HTTP (Streamable MCP) на localhost

Чтобы подключаться **по URL** (MCP Streamable HTTP), а не через stdio:

```bat
gradlew.bat :mcp-server:run --args="--transport=http --http-host=127.0.0.1 --http-port=3000 --http-path=/mcp"
```

```bat
gradlew.bat :mcp-server:run --args="--transport=http --http-host=127.0.0.1 --http-port=3000 --http-path=/mcp --sync-interval=PT30M"
```

- Эндпоинт по умолчанию: **`http://127.0.0.1:3000/mcp`** (хост/порт/путь настраиваются).
- Клиент должен поддерживать **Streamable HTTP** для MCP и использовать этот URL как базовый адрес сервера.
- Режим `--transport=stdio` (по умолчанию) оставлен для Claude Desktop и аналогов, которые поднимают процесс и говорят по stdin/stdout.

Рабочая директория — корень репозитория. Данные и логи: `%USERPROFILE%\.currency-watch\` — там же лежит SQLite (`currency.db`) и **файлы логов** `mcp-server.log` (ротация по дням: `mcp-server.yyyy-MM-dd.log`). Дублирование в **stderr** (не в stdout — в режиме stdio там протокол MCP).

## Подключение клиента (stdio)

Сервер использует stdin/stdout; в **stdout** нельзя писать ничего, кроме JSON-RPC MCP (логи — в файл и **stderr**). Любой лишний вывод в stdout приводит к обрыву сессии.

### Claude Desktop (Windows)

Рекомендуется **тихий** Gradle (`-q`) и **`--no-daemon`**, либо скрипт [scripts/run-mcp-stdio.bat](scripts/run-mcp-stdio.bat):

```json
{
  "mcpServers": {
    "currency-watch": {
      "command": "C:\\Users\\sts-dev\\AndroidStudioProjects\\ai_projects\\CourseAI_MCP\\scripts\\run-mcp-stdio.bat",
      "args": [],
      "cwd": "C:\\Users\\sts-dev\\AndroidStudioProjects\\ai_projects\\CourseAI_MCP"
    }
  }
}
```

Вариант через `gradlew.bat` напрямую:

```json
"command": "C:\\Users\\sts-dev\\AndroidStudioProjects\\ai_projects\\CourseAI_MCP\\gradlew.bat",
"args": ["-q", "--no-daemon", ":mcp-server:run", "--args=--sync-interval=PT1H"],
"cwd": "C:\\Users\\sts-dev\\AndroidStudioProjects\\ai_projects\\CourseAI_MCP"
```

Подставьте свои пути.

### Cursor / другие клиенты

Те же правила: **`gradlew.bat`** с аргументами **`-q`**, **`--no-daemon`**, затем **`:mcp-server:run`**, `cwd` — корень проекта; или путь к **`run-mcp-stdio.bat`**.

### Если пишет «connection closed» / соединение закрывается

1. Убедитесь, что запускаете с **`-q --no-daemon`** или через **`scripts/run-mcp-stdio.bat`**, либо через **`installDist`** + `bin\mcp-server.bat` (без Gradle).
2. Посмотрите **`%USERPROFILE%\.currency-watch\mcp-server.log`** и stderr — ошибка при старте (БД, сеть, ЦБ) завершит процесс.
3. В конфиге MCP не должно быть лишних аргументов, которые ломают разбор (проверьте кавычки в `args`).

## Инструмент `get_currency_summary`

- `hours` (integer, опционально): окно в часах, по умолчанию 24.
- `currencies` (массив строк, опционально): коды валют, например `["USD","EUR"]`. Если не задан — все валюты в окне.

Ответ содержит текстовую сводку и JSON с полями `avg`, `min`, `max`, `change`, `sampleCount` по каждой валюте.

## Модули

- `shared` — общая логика (API ЦБ, парсинг XML, репозиторий, агрегатор, SQLDelight, `expect`/`actual` для HTTP и планировщика). Реальная БД и MCP — на JVM; Android/iOS — заглушки планировщика.
- `mcp-server` — точка входа MCP (stdio или Streamable HTTP на Ktor/Netty, официальный `kotlin-sdk-server`).

## Планировщик

На JVM фоновая синхронизация с ЦБ запускается раз в час; при старте выполняется одна немедленная попытка загрузки.
