package com.minenergo.monitor.archive

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import com.minenergo.monitor.Config
import com.minenergo.monitor.log.AppLogger
import com.minenergo.monitor.security.PathSecurity
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Распаковка архивов с защитой от Zip-Slip и контролем суммарного объёма.
 *
 * Поддерживаемые форматы: .zip, .rar, .7z (см. ТЗ раздел 5).
 * Все три ветки реализованы единообразно: сначала проверяем размер
 * исходного архива и общее количество файлов, потом распаковываем
 * по одному файлу с проверкой пути и расширения.
 */
object ArchiveExtractor {

    private const val TAG = "Archive"

    data class Result(
        val outputDir: File,
        val unpackedCount: Int,
        val skippedCount: Int,
    )

    fun isSupported(filename: String): Boolean {
        val name = filename.lowercase()
        return name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z")
    }

    @Throws(IOException::class)
    fun extract(archiveFile: File): Result {
        val maxArchiveBytes = Config.MAX_ARCHIVE_SIZE_MB * 1024L * 1024L
        if (archiveFile.length() > maxArchiveBytes) {
            throw PathSecurity.SecurityException(
                "Архив больше ${Config.MAX_ARCHIVE_SIZE_MB} МБ"
            )
        }
        val outputDir = makeUnpackDir(archiveFile)
        AppLogger.i(TAG, "Распаковка ${archiveFile.name} -> ${outputDir.name}")
        val name = archiveFile.name.lowercase()
        return when {
            name.endsWith(".zip") -> extractZip(archiveFile, outputDir)
            name.endsWith(".rar") -> extractRar(archiveFile, outputDir)
            name.endsWith(".7z") -> extract7z(archiveFile, outputDir)
            else -> throw IOException("Неизвестный формат архива: $name")
        }
    }

    private fun extractZip(archiveFile: File, outputDir: File): Result {
        ZipFile(archiveFile).use { zip ->
            val entries = zip.entries().toList()
            ensureCount(entries.size)
            var totalSize = 0L
            var unpacked = 0
            var skipped = 0
            for (entry in entries) {
                if (entry.isDirectory) continue
                val target = PathSecurity.validateArchiveMemberPath(
                    entry.name, outputDir, Config.MAX_ARCHIVE_DEPTH
                )
                if (PathSecurity.isDangerousExtension(target)) {
                    AppLogger.w(TAG, "Пропущен опасный файл: ${entry.name}")
                    skipped++
                    continue
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().use { output ->
                        totalSize = streamCopyWithLimit(
                            input,
                            output,
                            previouslyWritten = totalSize,
                            entryDeclaredSize = entry.size,
                        )
                    }
                }
                unpacked++
            }
            return Result(outputDir, unpacked, skipped)
        }
    }

    private fun extractRar(archiveFile: File, outputDir: File): Result {
        Archive(archiveFile).use { archive ->
            val headers = mutableListOf<FileHeader>()
            while (true) {
                val h = archive.nextFileHeader() ?: break
                headers.add(h)
            }
            ensureCount(headers.size)
            var totalSize = 0L
            var unpacked = 0
            var skipped = 0
            for (header in headers) {
                if (header.isDirectory) continue
                val name = header.fileName ?: continue
                val target = PathSecurity.validateArchiveMemberPath(
                    name, outputDir, Config.MAX_ARCHIVE_DEPTH
                )
                if (PathSecurity.isDangerousExtension(target)) {
                    AppLogger.w(TAG, "Пропущен опасный файл: $name")
                    skipped++
                    continue
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    archive.extractFile(header, out)
                }
                totalSize += target.length()
                if (totalSize > Config.MAX_UNPACKED_TOTAL_SIZE_MB * 1024L * 1024L) {
                    target.delete()
                    throw PathSecurity.SecurityException(
                        "Превышен лимит распакованных данных " +
                            "${Config.MAX_UNPACKED_TOTAL_SIZE_MB} МБ"
                    )
                }
                unpacked++
            }
            return Result(outputDir, unpacked, skipped)
        }
    }

    private fun extract7z(archiveFile: File, outputDir: File): Result {
        // Первый проход: посчитать количество файлов и грубую сумму размеров.
        SevenZFile(archiveFile).use { sevenZ ->
            var fileCount = 0
            var declaredSize = 0L
            for (entry in sevenZ.entries) {
                if (!entry.isDirectory) {
                    fileCount++
                    if (entry.size > 0) declaredSize += entry.size
                }
            }
            ensureCount(fileCount)
            val maxBytes = Config.MAX_UNPACKED_TOTAL_SIZE_MB * 1024L * 1024L
            if (declaredSize > maxBytes) {
                throw PathSecurity.SecurityException(
                    "Распакованный размер 7z превышает ${Config.MAX_UNPACKED_TOTAL_SIZE_MB} МБ"
                )
            }
        }

        // Второй проход — реальная распаковка.
        SevenZFile(archiveFile).use { sevenZ ->
            var unpacked = 0
            var skipped = 0
            var totalSize = 0L
            while (true) {
                val entry: SevenZArchiveEntry = sevenZ.nextEntry ?: break
                if (entry.isDirectory) continue
                val target = PathSecurity.validateArchiveMemberPath(
                    entry.name, outputDir, Config.MAX_ARCHIVE_DEPTH
                )
                if (PathSecurity.isDangerousExtension(target)) {
                    AppLogger.w(TAG, "Пропущен опасный файл: ${entry.name}")
                    skipped++
                    continue
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = sevenZ.read(buffer)
                        if (read <= 0) break
                        totalSize += read
                        if (totalSize > Config.MAX_UNPACKED_TOTAL_SIZE_MB * 1024L * 1024L) {
                            out.flush()
                            target.delete()
                            throw PathSecurity.SecurityException(
                                "Превышен лимит распакованных данных " +
                                    "${Config.MAX_UNPACKED_TOTAL_SIZE_MB} МБ"
                            )
                        }
                        out.write(buffer, 0, read)
                    }
                }
                unpacked++
            }
            return Result(outputDir, unpacked, skipped)
        }
    }

    private fun ensureCount(count: Int) {
        if (count > Config.MAX_ARCHIVE_FILES_COUNT) {
            throw PathSecurity.SecurityException(
                "В архиве слишком много файлов: $count > ${Config.MAX_ARCHIVE_FILES_COUNT}"
            )
        }
    }

    /** Записывает поток в out, контролируя суммарный лимит [Config.MAX_UNPACKED_TOTAL_SIZE_MB]. */
    private fun streamCopyWithLimit(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        previouslyWritten: Long,
        entryDeclaredSize: Long,
    ): Long {
        val maxBytes = Config.MAX_UNPACKED_TOTAL_SIZE_MB * 1024L * 1024L
        if (entryDeclaredSize in 1..Long.MAX_VALUE &&
            previouslyWritten + entryDeclaredSize > maxBytes
        ) {
            throw PathSecurity.SecurityException(
                "Превышен лимит распаковки ${Config.MAX_UNPACKED_TOTAL_SIZE_MB} МБ"
            )
        }
        val buffer = ByteArray(64 * 1024)
        var totalSize = previouslyWritten
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            totalSize += read
            if (totalSize > maxBytes) {
                throw PathSecurity.SecurityException(
                    "Превышен лимит распаковки ${Config.MAX_UNPACKED_TOTAL_SIZE_MB} МБ"
                )
            }
            output.write(buffer, 0, read)
        }
        return totalSize
    }

    private fun makeUnpackDir(archiveFile: File): File {
        val parent = archiveFile.parentFile ?: throw IOException("Нет каталога архива")
        val baseName = archiveFile.nameWithoutExtension + "_unpacked"
        var candidate = File(parent, baseName)
        var idx = 1
        while (candidate.exists()) {
            candidate = File(parent, "${baseName}_$idx")
            idx++
        }
        candidate.mkdirs()
        return candidate
    }

    /** Convenience: автозакрытие для junrar Archive. */
    private inline fun <T> Archive.use(block: (Archive) -> T): T {
        try {
            return block(this)
        } finally {
            try { close() } catch (_: Throwable) { }
        }
    }
}
