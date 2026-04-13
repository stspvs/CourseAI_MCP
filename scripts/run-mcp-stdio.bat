@echo off
REM Запуск MCP в режиме stdio без лишнего вывода Gradle в stdout (иначе клиент рвёт соединение).
cd /d "%~dp0.."
call gradlew.bat -q --no-daemon :mcp-server:run %*
