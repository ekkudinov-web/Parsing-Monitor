package com.minenergo.monitor.security

import com.minenergo.monitor.Config
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.io.IOException

/**
 * Утилиты безопасной работы с файлами и URL.
 *
 * Разрешённые для скачивания хосты теперь определяются динамически —
 * по списку активных сайтов (см. [ensureUrlAllowed]).
 */
object PathSecurity {

    class SecurityException(message: String) : IOException(message)

    /**
     * Проверяет, что схема URL — http/https и хост входит в список разрешённых.
     * Сравнение хостов регистронезависимое.
     */
    fun ensureUrlAllowed(url: String, allowedHosts: Set<String>) {
        val parsed = url.toHttpUrlOrNull()
            ?: throw SecurityException("Некорректный URL: $url")
        val scheme = parsed.scheme.lowercase()
        if (scheme != "http" && scheme != "https") {
            throw SecurityException("Недопустимая схема URL: $scheme")
        }
        val host = parsed.host.lowercase()
        val normalized = allowedHosts.map { it.lowercase() }.toSet()
        if (host !in normalized) {
            throw SecurityException("Скачивание с домена не разрешено: $host")
        }
    }

    fun safeJoin(baseDir: File, vararg parts: String): File {
        val base = baseDir.canonicalFile
        var current = base
        for (part in parts) current = File(current, part)
        val resolved = current.canonicalFile
        if (resolved != base &&
            !resolved.path.startsWith(base.path + File.separator)
        ) throw SecurityException("Попытка записи вне разрешённой папки: $resolved")
        return resolved
    }

    fun validateArchiveMemberPath(memberName: String, targetDir: File, maxDepth: Int): File {
        val normalized = memberName.replace('\\', '/')
        if (normalized.startsWith("/") || ABSOLUTE_WIN_RE.matches(normalized)) {
            throw SecurityException("Абсолютный путь в архиве запрещён: $memberName")
        }
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) {
            throw SecurityException("Путь с ../ в архиве запрещён: $memberName")
        }
        if (parts.size > maxDepth) {
            throw SecurityException("Превышена глубина вложенности архива: $memberName")
        }
        return safeJoin(targetDir, *parts.toTypedArray())
    }

    fun isDangerousExtension(file: File, dangerousExtensions: Set<String> = Config.DANGEROUS_EXTENSIONS): Boolean {
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        if (dotIndex < 0) return false
        val ext = name.substring(dotIndex).lowercase()
        return ext in dangerousExtensions
    }

    fun sanitizeFilename(value: String, fallback: String = "file"): String {
        val cleaned = value
            .trim()
            .replace("\u0000", "")
            .replace('/', ' ')
            .replace('\\', ' ')
            .replace(Regex("[\\u0000-\\u001F<>:\"|?*]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.', '_')
        val safe = cleaned.ifEmpty { fallback }
        return if (safe.length > 180) safe.substring(0, 180) else safe
    }

    private val ABSOLUTE_WIN_RE = Regex("^[A-Za-z]:/.*")
}
