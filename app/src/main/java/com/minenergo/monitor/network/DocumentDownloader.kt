package com.minenergo.monitor.network

import com.minenergo.monitor.Config
import com.minenergo.monitor.archive.ArchiveExtractor
import com.minenergo.monitor.data.Document
import com.minenergo.monitor.data.DownloadResult
import com.minenergo.monitor.log.AppLogger
import com.minenergo.monitor.security.PathSecurity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Скачивание документов в приватное хранилище приложения и
 * (опционально) распаковка архивов.
 *
 * @param baseDir корневая папка для всех скачанных файлов
 *                (обычно `<filesDir>/downloads`).
 * @param allowedHosts множество разрешённых хостов — формируется
 *                    из активных сайтов в [com.minenergo.monitor.MainViewModel].
 */
class DocumentDownloader(
    private val baseDir: File,
    private val allowedHosts: Set<String>,
) {

    private val client = HttpClientFactory.get()

    fun download(document: Document): DownloadResult {
        val tag = "Download"
        try {
            PathSecurity.ensureUrlAllowed(document.url, allowedHosts)
        } catch (e: PathSecurity.SecurityException) {
            AppLogger.w(tag, "Блокировка домена для ${document.url}: ${e.message}")
            return DownloadResult(document, downloaded = false, error = e.message)
        }

        // Структура: baseDir/<siteId>/<yyyy-MM-dd>/...
        val targetDir = PathSecurity.safeJoin(
            baseDir,
            sanitizeSegment(document.siteId),
            document.publicationDateIso,
        )
        if (!targetDir.exists()) targetDir.mkdirs()

        val maxBytes = Config.MAX_DOWNLOAD_FILE_SIZE_MB * 1024L * 1024L
        val request = Request.Builder()
            .url(document.url)
            .header("User-Agent", Config.USER_AGENT)
            .build()

        var lastError: Throwable? = null
        repeat(Config.RETRY_COUNT) { attempt ->
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code} при скачивании файла")
                    }
                    val body = response.body
                        ?: throw IOException("Пустой ответ при скачивании")
                    val contentLength = body.contentLength()
                    if (contentLength in 1..Long.MAX_VALUE && contentLength > maxBytes) {
                        throw IOException("Файл больше лимита ($contentLength > $maxBytes)")
                    }
                    val filename = chooseFilename(
                        document = document,
                        contentDisposition = response.header("Content-Disposition"),
                    )
                    val targetFile = uniquePath(PathSecurity.safeJoin(targetDir, filename))
                    var written = 0L
                    body.byteStream().use { input ->
                        targetFile.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val read = input.read(buffer)
                                if (read <= 0) break
                                written += read
                                if (written > maxBytes) {
                                    output.flush()
                                    targetFile.delete()
                                    throw IOException(
                                        "Файл превышает лимит ${Config.MAX_DOWNLOAD_FILE_SIZE_MB} МБ"
                                    )
                                }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    AppLogger.i(tag, "Скачан: ${targetFile.absolutePath} ($written байт)")
                    return maybeUnpack(document, targetFile)
                }
            } catch (e: Throwable) {
                lastError = e
                AppLogger.w(tag, "Попытка ${attempt + 1} скачивания не удалась: ${e.message}")
                Thread.sleep((400L * (attempt + 1)).coerceAtMost(2500L))
            }
        }
        val message = lastError?.message ?: "Неизвестная ошибка"
        AppLogger.e(tag, "Не удалось скачать ${document.url}: $message", lastError)
        return DownloadResult(document, downloaded = false, error = message)
    }

    private fun maybeUnpack(document: Document, file: File): DownloadResult {
        if (!ArchiveExtractor.isSupported(file.name)) {
            return DownloadResult(
                document = document,
                downloaded = true,
                filePath = file.absolutePath,
            )
        }
        return try {
            val unpack = ArchiveExtractor.extract(file)
            DownloadResult(
                document = document,
                downloaded = true,
                filePath = file.absolutePath,
                archiveUnpacked = true,
                unpackDir = unpack.outputDir.absolutePath,
                unpackedFilesCount = unpack.unpackedCount,
                skippedFilesCount = unpack.skippedCount,
            )
        } catch (e: Throwable) {
            AppLogger.e("Download", "Ошибка распаковки ${file.name}: ${e.message}", e)
            DownloadResult(
                document = document,
                downloaded = true,
                filePath = file.absolutePath,
                archiveUnpacked = false,
                archiveError = e.message ?: "Неизвестная ошибка распаковки",
            )
        }
    }

    private fun chooseFilename(document: Document, contentDisposition: String?): String {
        contentDispositionFilename(contentDisposition)?.let { return PathSecurity.sanitizeFilename(it) }
        val pathName = runCatching {
            val path = URI(document.url).path ?: return@runCatching null
            val raw = path.substringAfterLast('/')
            URLDecoder.decode(raw, Charsets.UTF_8)
        }.getOrNull()
        if (!pathName.isNullOrBlank()) return PathSecurity.sanitizeFilename(pathName)
        val date = LocalDate.parse(document.publicationDateIso).format(DateTimeFormatter.ISO_DATE)
        val ext = document.extension ?: ""
        val safeTitle = PathSecurity.sanitizeFilename(document.title)
        return "${date}_$safeTitle$ext"
    }

    private fun contentDispositionFilename(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val starMatch =
            Regex("""filename\*\s*=\s*([^']*)'[^']*'([^;]+)""", RegexOption.IGNORE_CASE).find(header)
        if (starMatch != null) {
            val charsetName = starMatch.groupValues[1].ifBlank { "UTF-8" }
            val raw = starMatch.groupValues[2].trim().trim('"')
            return runCatching { URLDecoder.decode(raw, charsetName) }.getOrNull()
        }
        val plain = Regex("""filename\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE).find(header)
        return plain?.groupValues?.get(1)?.trim()
    }

    private fun uniquePath(path: File): File {
        if (!path.exists()) return path
        val parent = path.parentFile ?: return path
        val name = path.nameWithoutExtension
        val ext = path.extension.let { if (it.isBlank()) "" else ".$it" }
        var idx = 1
        while (true) {
            val candidate = File(parent, "${name}_$idx$ext")
            if (!candidate.exists()) return candidate
            idx++
        }
    }

    private fun sanitizeSegment(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(64).ifEmpty { "site" }

    companion object {
        /** Извлекает хост из URL для формирования allowedHosts. */
        fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host?.lowercase()
    }
}
