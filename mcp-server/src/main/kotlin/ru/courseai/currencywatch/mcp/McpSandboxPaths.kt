package ru.courseai.currencywatch.mcp

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardOpenOption

internal sealed class SandboxPathResult {
    data class Ok(val file: File) : SandboxPathResult()
    data class Err(val message: String) : SandboxPathResult()
}

/**
 * Разрешает относительный путь внутри [dataDir] без выхода за пределы каталога (после canonical).
 */
internal fun resolveSandboxFile(dataDir: File, relativePath: String): SandboxPathResult {
    val trimmed = relativePath.trim()
    if (trimmed.isEmpty()) {
        return SandboxPathResult.Err("Параметр path не может быть пустым.")
    }
    if (File(trimmed).isAbsolute) {
        return SandboxPathResult.Err("Укажите относительный путь внутри каталога данных, без абсолютного пути.")
    }
    val root = try {
        dataDir.canonicalFile
    } catch (e: Exception) {
        return SandboxPathResult.Err("Не удалось определить каталог данных: ${e.message ?: e::class.simpleName}")
    }
    val candidate = File(root, trimmed)
    val canonical = try {
        candidate.canonicalFile
    } catch (e: Exception) {
        return SandboxPathResult.Err("Некорректный путь: ${e.message ?: e::class.simpleName}")
    }
    val rootPath = root.toPath()
    val resolved = canonical.toPath()
    if (!resolved.startsWith(rootPath)) {
        return SandboxPathResult.Err("Путь выходит за пределы разрешённого каталога данных.")
    }
    return SandboxPathResult.Ok(canonical)
}

internal data class SandboxWriteOutcome(
    val absolutePath: String,
    val modeLabel: String,
    val bytesWritten: Int,
)

/**
 * Записывает UTF-8 текст; родительские каталоги создаются при необходимости.
 */
internal fun writeSandboxUtf8(
    target: File,
    content: String,
    append: Boolean,
): Result<SandboxWriteOutcome> {
    val parent = target.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
        return Result.failure(IOException("Не удалось создать каталог: ${parent.absolutePath}"))
    }
    val existedBefore = target.exists()
    val bytes = content.toByteArray(StandardCharsets.UTF_8)
    return try {
        val path = target.toPath()
        if (append) {
            Files.write(
                path,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } else {
            Files.write(
                path,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        }
        val modeLabel = when {
            append && existedBefore -> "дописывание"
            append && !existedBefore -> "создание"
            !append && existedBefore -> "перезапись"
            else -> "создание"
        }
        Result.success(
            SandboxWriteOutcome(
                absolutePath = target.absolutePath,
                modeLabel = modeLabel,
                bytesWritten = bytes.size,
            ),
        )
    } catch (e: Exception) {
        Result.failure(e)
    }
}
